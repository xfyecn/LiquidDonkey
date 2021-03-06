/* 
 * The MIT License
 *
 * Copyright 2015 Ahseya.
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
 */
package com.github.horrorho.liquiddonkey.crypto;

import net.jcip.annotations.NotThreadSafe;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESWrapEngine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * AES Key Wrap.
 *
 * @author ahseya
 */
@NotThreadSafe
public final class AESWrap {

    private final AESWrapEngine engine = new AESWrapEngine();

    public static AESWrap create() {
        return new AESWrap();
    }

    AESWrap() {
    }

    public byte[] unwrap(byte[] keyEncryptionKey, byte[] key) throws InvalidCipherTextException {
        engine.init(false, new KeyParameter(keyEncryptionKey));
        return engine.unwrap(key, 0, key.length);
    }

    public byte[] wrap(byte[] keyEncryptionKey, byte[] key) {
        engine.init(true, new KeyParameter(keyEncryptionKey));
        return engine.wrap(key, 0, key.length);
    }
}
