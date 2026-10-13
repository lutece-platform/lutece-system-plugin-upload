/*
 * Copyright (c) 2002-2014, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.upload.business;

import java.io.Serializable;
import java.util.Date;

/**
 * A file or a directory of an upload directory
 */
public class UploadedFile implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final String _strName;
    private final long _lSize;
    private final long _lLastModified;
    private final boolean _bDirectory;

    /**
     * Creates an uploaded file
     *
     * @param strName
     *            the name
     * @param lSize
     *            the size in bytes, the whole content for a directory
     * @param lLastModified
     *            the last modification time, in milliseconds since the epoch
     * @param bDirectory
     *            true for a directory
     */
    public UploadedFile( String strName, long lSize, long lLastModified, boolean bDirectory )
    {
        _strName = strName;
        _lSize = lSize;
        _lLastModified = lLastModified;
        _bDirectory = bDirectory;
    }

    /**
     * Returns the name
     *
     * @return the name
     */
    public String getName( )
    {
        return _strName;
    }

    /**
     * Returns the size in bytes
     *
     * @return the size
     */
    public long getSize( )
    {
        return _lSize;
    }

    /**
     * Returns the last modification date
     *
     * @return the date
     */
    public Date getDateLastModified( )
    {
        return new Date( _lLastModified );
    }

    /**
     * Tells whether this is a directory
     *
     * @return true for a directory
     */
    public boolean isDirectory( )
    {
        return _bDirectory;
    }
}
