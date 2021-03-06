/*
 * The MIT License
 *
 * Copyright 2015 Ahseya.
 *
 * Permission is hereby granted, free from charge, to any person obtaining a copy
 * from this software and associated documentation list (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies from the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions from the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.horrorho.liquiddonkey.cloud.client;

import static com.github.horrorho.liquiddonkey.cloud.client.Util.path;
import com.github.horrorho.liquiddonkey.cloud.protobuf.ChunkServer;
import com.github.horrorho.liquiddonkey.cloud.protobuf.ICloud;
import com.github.horrorho.liquiddonkey.cloud.protobuf.ProtoBufArray;
import com.github.horrorho.liquiddonkey.exception.BadDataException;
import com.github.horrorho.liquiddonkey.http.ResponseHandlerFactory;
import com.github.horrorho.liquiddonkey.settings.Markers;
import com.github.horrorho.liquiddonkey.util.Bytes;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import net.jcip.annotations.Immutable;
import net.jcip.annotations.ThreadSafe;
import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * FileGroupsClient.
 *
 * @author Ahseya
 */
@Immutable
@ThreadSafe
public final class FileGroupsClient {

    public static FileGroupsClient create() {

        return new FileGroupsClient(
                defaultFilesGroupsHandler,
                defaultMbsFileAuthTokenListHandler,
                Headers.create());
    }

    private static final ResponseHandler<ChunkServer.FileGroups> defaultFilesGroupsHandler
            = ResponseHandlerFactory.of(ChunkServer.FileGroups.PARSER::parseFrom);
    private static final ResponseHandler<List<ICloud.MBSFileAuthToken>> defaultMbsFileAuthTokenListHandler
            = ResponseHandlerFactory.of(inputStream -> ProtoBufArray.decode(inputStream, ICloud.MBSFileAuthToken.PARSER));

    private static final Logger logger = LoggerFactory.getLogger(FileGroupsClient.class);
    private static final Marker marker = MarkerFactory.getMarker(Markers.CLIENT);

    private final ResponseHandler<ChunkServer.FileGroups> filesGroupsHandler;
    private final ResponseHandler<List<ICloud.MBSFileAuthToken>> mbsFileAuthTokenListHandler;
    private final Headers headers;

    FileGroupsClient(
            ResponseHandler<ChunkServer.FileGroups> filesGroupsHandler,
            ResponseHandler<List<ICloud.MBSFileAuthToken>> mbsFileAuthTokenListHandler,
            Headers headers) {

        this.filesGroupsHandler = Objects.requireNonNull(filesGroupsHandler);
        this.mbsFileAuthTokenListHandler = Objects.requireNonNull(mbsFileAuthTokenListHandler);
        this.headers = Objects.requireNonNull(headers);
    }

    public List<ICloud.MBSFileAuthToken> getFiles(
            HttpClient client,
            String dsPrsID,
            String mmeAuthToken,
            String mobileBackupUrl,
            String udid,
            String snapshot,
            Collection<ICloud.MBSFile> files
    ) throws BadDataException, IOException {

        logger.trace("<< getFiles() < dsPrsID: {} udid: {} snapshot: {} files: {}",
                dsPrsID, udid, snapshot, files.size());
        logger.debug(marker, "-- getFiles() < files: {}", files);

        List<ICloud.MBSFile> postData = files.stream()
                .map(file -> ICloud.MBSFile.newBuilder().setFileID(file.getFileID()).build())
                .collect(Collectors.toList());

        byte[] encoded;
        try {
            encoded = ProtoBufArray.encode(postData);
        } catch (IOException ex) {
            throw new BadDataException(ex);
        }

        String uri = path(mobileBackupUrl, "mbs", dsPrsID, udid, snapshot, "getFiles");

        HttpPost post = new HttpPost(uri);
        headers.mobileBackupHeaders(dsPrsID, mmeAuthToken).stream().forEach(post::addHeader);
        post.setEntity(new ByteArrayEntity(encoded));
        List<ICloud.MBSFileAuthToken> tokens = client.execute(post, mbsFileAuthTokenListHandler);

        logger.debug(marker, "-- getFiles() > tokens: {}", tokens);
        logger.trace(">> getFiles() > {}", tokens.size());
        return tokens;
    }

    public ChunkServer.FileGroups authorizeGet(
            HttpClient client,
            String dsPrsID,
            String contentUrl,
            ICloud.MBSFileAuthTokens authTokens
    ) throws IOException {

        logger.trace("<< authorizeGet() < dsPrsID: {} tokens size: {}}", dsPrsID, authTokens.getTokensCount());

        final ChunkServer.FileGroups fileGroups;
        if (authTokens.getTokensCount() == 0) {
            fileGroups = ChunkServer.FileGroups.getDefaultInstance();

        } else {
            Header mmcsAuth = headers.mmcsAuth(Bytes.hex(authTokens.getTokens(0).getFileID())
                    + " " + authTokens.getTokens(0).getAuthToken());

            String uri = path(contentUrl, dsPrsID, "authorizeGet");
            RequestBuilder builder = RequestBuilder.get(uri).addHeader(mmcsAuth);
            headers.contentHeaders(dsPrsID).stream().forEach(builder::addHeader);
            HttpUriRequest post = builder.setEntity(new ByteArrayEntity(authTokens.toByteArray())).build();
            fileGroups = client.execute(post, filesGroupsHandler);
        }

        logger.debug(marker, "-- authorizeGet() > fileError: {}", fileGroups.getFileErrorList());
        logger.debug(marker, "-- authorizeGet() > fileChunkError: {}", fileGroups.getFileChunkErrorList());
        logger.debug(marker, "-- authorizeGet() > fileGroups: {}", fileGroups);
        logger.trace(">> authorizeGet() > {}", fileGroups.getFileGroupsCount());
        return fileGroups;
    }
}
