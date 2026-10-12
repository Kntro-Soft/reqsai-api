package com.kntro.reqsai.codebase.api;

import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Links to the client's code on the code host. */
public final class CodeLinks {

    private CodeLinks() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * The file at a commit, with its lines highlighted: {@code <repository>/blob/<sha>/<path>#L<a>-L<b>}
     * ({@code #L<a>} for one line, no anchor without lines). {@code repositoryUrl} is the repository's web
     * address ({@code https://github.com/owner/name}).
     */
    public static String blobUrl(String repositoryUrl, String commitSha, String path,
                                 @Nullable Integer lineStart, @Nullable Integer lineEnd) {
        StringBuilder url = new StringBuilder(repositoryUrl.replaceAll("/+$", ""))
                .append("/blob/").append(commitSha).append('/');
        String[] segments = path.replace('\\', '/').replaceAll("^/+", "").split("/");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) url.append('/');
            url.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        if (lineStart != null && lineStart > 0) {
            url.append("#L").append(lineStart);
            if (lineEnd != null && lineEnd > lineStart) url.append("-L").append(lineEnd);
        }
        return url.toString();
    }
}
