/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pinterest.secor.io.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.hadoop.io.compress.CompressionCodec;

import com.google.common.io.CountingOutputStream;
import com.pinterest.secor.common.LogFilePath;
import com.pinterest.secor.io.FileReaderWriter;
import com.pinterest.secor.io.KeyValue;

/**
 * 
 * Delimited Text File Reader Writer with Compression
 * 
 * @author Praveen Murugesan (praveen@uber.com)
 *
 */
public class DelimitedTextFileReaderWriter implements FileReaderWriter {

    // delimiter used between messages
    private static final byte DELIMITER = '\n';

    private final CountingOutputStream countingStream;
    private final BufferedOutputStream writer;

    private final BufferedInputStream reader;
    private long offset;

    // constructor
    public DelimitedTextFileReaderWriter(LogFilePath path,
            CompressionCodec codec, FileReaderWriter.Type type)
            throws FileNotFoundException, IOException {

        File logFile = new File(path.getLogFilePath());
        logFile.getParentFile().mkdirs();
        if (type == FileReaderWriter.Type.Reader) {
            InputStream inputStream = new FileInputStream(new File(
                    path.getLogFilePath()));
            this.reader = (codec == null) ? new BufferedInputStream(inputStream)
                    : new BufferedInputStream(
                            codec.createInputStream(inputStream));
            this.offset = path.getOffset();
            this.countingStream = null;
            this.writer = null;
        } else if (type == FileReaderWriter.Type.Writer) {
            this.countingStream = new CountingOutputStream(
                    new FileOutputStream(logFile));
            this.writer = (codec == null) ? new BufferedOutputStream(
                    this.countingStream) : new BufferedOutputStream(
                    codec.createOutputStream(this.countingStream));
            this.reader = null;
        } else {
            throw new IllegalArgumentException("Undefined File Type: " + type);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.writer != null) {
            this.writer.close();
        }
        if (this.reader != null) {
            this.reader.close();
        }
    }

    @Override
    public long getLength() throws IOException {
        assert this.countingStream != null;
        return this.countingStream.getCount();
    }

    @Override
    public void write(long key, byte[] value) throws IOException {
        assert this.writer != null;
        this.writer.write(value);
        this.writer.write(DELIMITER);
    }

    @Override
    public KeyValue next() throws IOException {
        assert this.reader != null;
        ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
        int nextByte;
        while ((nextByte = reader.read()) != DELIMITER) {
            if (nextByte == -1) { // end of stream?
                if (messageBuffer.size() == 0) { // if no byte read
                    return null;
                } else { // if bytes followed by end of stream: framing error
                    throw new EOFException(
                            "Non-empty message without delimiter");
                }
            }
            messageBuffer.write(nextByte);
        }
        return new KeyValue(this.offset++, messageBuffer.toByteArray());
    }

}
