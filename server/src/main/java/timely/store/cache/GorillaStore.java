package timely.store.cache;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.StampedLock;

import fi.iki.yak.ts.compression.gorilla.GorillaDecompressor;
import fi.iki.yak.ts.compression.gorilla.LongArrayInput;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import timely.model.Metric;

public class GorillaStore {

    private Queue<WrappedGorillaCompressor> archivedCompressors = new LinkedList<WrappedGorillaCompressor>();
    private StampedLock archivedCompressorLock = new StampedLock();
    private StampedLock currentCompressorLock = new StampedLock();

    transient private WrappedGorillaCompressor current = null;
    transient private List<Metric> metricCache = new ArrayList<>();

    private long oldestTimestamp = Long.MAX_VALUE;
    private long newestTimestamp = -1;

    public GorillaStore() {

    }

    public GorillaStore(FileSystem fs, String metric, timely.Configuration conf) throws IOException {
        String baseDir = "/timely/cache";
        Path directory = new Path(baseDir + "/" + metric);
        List<WrappedGorillaCompressor> compressors = readCompressors(fs, directory);
        long stamp = archivedCompressorLock.writeLock();
        try {
            archivedCompressors.addAll(compressors);
        } finally {
            archivedCompressorLock.unlockWrite(stamp);
        }
    }

    private WrappedGorillaCompressor getCompressor(long timestamp, long lockStamp) {
        if (current == null) {
            if (oldestTimestamp == Long.MAX_VALUE) {
                oldestTimestamp = timestamp;
            }
            newestTimestamp = timestamp;
            long stamp = lockStamp == 0 ? currentCompressorLock.writeLock() : lockStamp;
            try {
                current = new WrappedGorillaCompressor(timestamp);
            } finally {
                if (lockStamp == 0) {
                    currentCompressorLock.unlockWrite(stamp);
                }
            }
        }
        return current;
    }

    public long ageOffArchivedCompressors(long maxAge) {
        long numRemoved = 0;
        long oldestRemainingTimestamp = Long.MAX_VALUE;
        long stamp = archivedCompressorLock.writeLock();
        try {
            if (!archivedCompressors.isEmpty()) {
                Iterator<WrappedGorillaCompressor> itr = archivedCompressors.iterator();
                long now = System.currentTimeMillis();
                while (itr.hasNext()) {
                    WrappedGorillaCompressor c = itr.next();
                    long timeSinceNewestTimestamp = now - c.getNewestTimestamp();
                    if (timeSinceNewestTimestamp >= maxAge) {
                        itr.remove();
                        numRemoved++;
                    } else {
                        if (c.getOldestTimestamp() < oldestRemainingTimestamp) {
                            oldestRemainingTimestamp = c.getOldestTimestamp();
                        }
                    }
                }
                if (oldestRemainingTimestamp < Long.MAX_VALUE) {
                    oldestTimestamp = oldestRemainingTimestamp;
                }
            }
        } finally {
            archivedCompressorLock.unlockWrite(stamp);
        }
        return numRemoved;
    }

    protected void writeCompressor(String metric, WrappedGorillaCompressor wrappedGorillaCompressor)
            throws IOException {

        try {
            Configuration configuration = new Configuration();
            FileSystem fs = FileSystem.get(new URI("hdfs://localhost:8020"), configuration);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String baseDir = "/timely/cache";
            Path directory = new Path(baseDir + "/" + metric);
            String fileName = metric + "-" + sdf.format(new Date(wrappedGorillaCompressor.getOldestTimestamp()));
            Path outputPath = new Path(directory, fileName);
            if (!fs.exists(directory)) {
                fs.mkdirs(directory);
            }
            if (fs.exists(outputPath)) {
                throw new IOException("output path exists");
            }
            OutputStream os = fs.create(outputPath);
            // write object to hdfs file
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(wrappedGorillaCompressor);
            oos.close();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    protected List<WrappedGorillaCompressor> readCompressors(FileSystem fs, Path directory) throws IOException {

        List<WrappedGorillaCompressor> compressors = new ArrayList<>();

        ObjectInputStream ois = null;
        try {
            FileStatus fileStatus[] = fs.listStatus(directory);
            for (FileStatus status : fileStatus) {
                FSDataInputStream inputStream = fs.open(status.getPath());
                ois = new ObjectInputStream(inputStream);
                WrappedGorillaCompressor copyCompressor = (WrappedGorillaCompressor) ois.readObject();
                compressors.add(copyCompressor);
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        } finally {
            if (ois != null) {
                ois.close();
            }
        }
        return compressors;
    }

    public void archiveCurrentCompressor() {

        long archiveStamp = archivedCompressorLock.writeLock();
        long currentStamp = currentCompressorLock.writeLock();
        try {
            if (current != null) {
                current.close();
                archivedCompressors.add(current);
                current = null;
            }
        } finally {
            archivedCompressorLock.unlockWrite(archiveStamp);
            currentCompressorLock.unlockWrite(currentStamp);
        }
    }

    public List<WrappedGorillaDecompressor> getDecompressors(long begin, long end) {

        List<WrappedGorillaDecompressor> decompressors = new ArrayList<>();

        long stamp = archivedCompressorLock.readLock();
        try {
            for (WrappedGorillaCompressor r : archivedCompressors) {
                if (r.inRange(begin, end)) {
                    GorillaDecompressor d = new GorillaDecompressor(new LongArrayInput(r.getCompressorOutput()));
                    // use -1 length since this compressor is closed
                    decompressors.add(new WrappedGorillaDecompressor(d, -1));
                }
            }
        } finally {
            archivedCompressorLock.unlockRead(stamp);
        }

        // as long as begin is inRange, we should use the data in the current
        // compressor as well
        stamp = currentCompressorLock.readLock();
        try {
            if (current != null && current.inRange(begin, end)) {
                LongArrayInput decompressorByteBufferInput = new LongArrayInput(current.getCompressorOutput());
                GorillaDecompressor d = new GorillaDecompressor(decompressorByteBufferInput);

                WrappedGorillaDecompressor wd = new WrappedGorillaDecompressor(d, current.getNumEntries());
                decompressors.add(wd);
            }
        } finally {
            currentCompressorLock.unlockRead(stamp);
        }
        return decompressors;
    }

    public void flush() {

        List<Metric> tempCache = new ArrayList<>();
        synchronized (metricCache) {
            tempCache.addAll(metricCache);
            metricCache.clear();
        }

        long stamp = currentCompressorLock.writeLock();
        try {
            WrappedGorillaCompressor c = null;
            for (Metric m : tempCache) {
                long ts = m.getValue().getTimestamp();
                double v = m.getValue().getMeasure();
                if (ts > newestTimestamp) {
                    newestTimestamp = ts;
                    if (c == null) {
                        c = getCompressor(ts, stamp);
                    }
                    c.addValue(ts, v);
                }
            }
        } finally {
            currentCompressorLock.unlockWrite(stamp);
        }
    }

    public void addValue(Metric metric) {
        synchronized (metricCache) {
            metricCache.add(metric);
        }
    }

    public void addValue(long timestamp, double value) {

        // values must be inserted in order
        if (timestamp >= newestTimestamp) {
            newestTimestamp = timestamp;
            long stamp = currentCompressorLock.writeLock();
            try {
                getCompressor(timestamp, stamp).addValue(timestamp, value);
            } finally {
                currentCompressorLock.unlockWrite(stamp);
            }
        }
    }

    public long getNewestTimestamp() {
        return newestTimestamp;
    }

    public long getOldestTimestamp() {
        return oldestTimestamp;
    }

    public long getNumEntries() {

        long numEntries = 0;
        long stamp = currentCompressorLock.readLock();
        try {
            if (current != null) {
                numEntries += current.getNumEntries();
            }
        } finally {
            currentCompressorLock.unlockRead(stamp);
        }
        stamp = archivedCompressorLock.readLock();
        try {
            if (!archivedCompressors.isEmpty()) {
                Iterator<WrappedGorillaCompressor> itr = archivedCompressors.iterator();
                while (itr.hasNext()) {
                    WrappedGorillaCompressor c = itr.next();
                    numEntries += c.getNumEntries();
                }
            }
        } finally {
            archivedCompressorLock.unlockRead(stamp);
        }
        return numEntries;
    }
}
