/*=============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Che-Hung Lin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *===========================================================================*/
package ch.lin.youtube.hub.backend.api.app.service;

import java.io.InputStream;

/**
 * Abstract service interface for storage operations. This enables the Strategy
 * Pattern to switch between Local Storage and Cloud Object Storage (e.g.,
 * S3/SeaweedFS).
 */
public interface StorageService {

    /**
     * Stores an object from an input stream.
     *
     * @param objectKey the unique identifier or path for the object
     * @param inputStream the data stream of the object
     * @param contentLength the total size of the data in bytes
     * @param contentType the MIME type of the data
     * @throws Exception if an I/O or storage-specific error occurs
     */
    void store(String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception;

    /**
     * Checks whether an object exists in the storage.
     *
     * @param objectKey the unique identifier or path for the object
     * @return true if the object exists, false otherwise
     */
    boolean exists(String objectKey);

    /**
     * Returns the publicly accessible URL or path for the stored object.
     *
     * @param objectKey the unique identifier or path for the object
     * @return the resolved URL or path for the client to access the file
     */
    String getFileAccessUrl(String objectKey);

}
