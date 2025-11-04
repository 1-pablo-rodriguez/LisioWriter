package writer.update;

public interface ProgressListener {
 void onProgress(long readBytes, long expectedBytes);
 void onStatus(String message);
}

