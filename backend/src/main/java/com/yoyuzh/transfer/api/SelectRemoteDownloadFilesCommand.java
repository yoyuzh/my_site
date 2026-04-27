package com.yoyuzh.transfer.api;

import java.util.List;

public record SelectRemoteDownloadFilesCommand(
        List<String> fileKeys
) {
}
