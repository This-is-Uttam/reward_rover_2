package com.app.rewardcycle.Utils;

public interface DownloadProgressListener {
    void update(long bytesRead, long contentLength, boolean done);
}