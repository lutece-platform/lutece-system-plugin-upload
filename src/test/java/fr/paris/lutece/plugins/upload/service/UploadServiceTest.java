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
package fr.paris.lutece.plugins.upload.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.paris.lutece.plugins.upload.business.UploadedFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * Tests of UploadService
 */
class UploadServiceTest
{
    private static final byte [ ] CONTENT = "content".getBytes( StandardCharsets.UTF_8 );

    private final UploadService _service = new UploadService( );

    @TempDir
    File _directory;

    /**
     * A name leading out of the directory resolves to nothing
     */
    @Test
    void testResolveRefusesTraversal( )
    {
        assertNull( _service.resolve( _directory, "../outside.txt" ) );
        assertNull( _service.resolve( _directory, "a/../../outside.txt" ) );
        assertNull( _service.resolve( _directory, "." ) );
        assertNull( _service.resolve( _directory, "" ) );
        assertEquals( new File( _directory, "a/b.txt" ).getAbsoluteFile( ), _service.resolve( _directory, "a/b.txt" ) );
    }

    /**
     * A file is written under its cleaned name, once
     *
     * @throws IOException
     *             if the file cannot be written
     */
    @Test
    void testSaveFile( ) throws IOException
    {
        String strName = _service.saveFile( _directory, "mon fichier é.txt", new ByteArrayInputStream( CONTENT ) );

        assertEquals( "mon_fichier_e.txt", strName );
        assertArrayEquals( CONTENT, Files.readAllBytes( new File( _directory, strName ).toPath( ) ) );
        assertThrows( FileAlreadyExistsException.class, ( ) -> _service.saveFile( _directory, "mon fichier é.txt", new ByteArrayInputStream( CONTENT ) ) );
        assertThrows( AccessDeniedException.class, ( ) -> _service.saveFile( _directory, "page.JSP", new ByteArrayInputStream( CONTENT ) ) );
        assertFalse( new File( _directory, "page.JSP" ).exists( ) );
    }

    /**
     * An archive is extracted, its unsafe entries skipped
     *
     * @throws IOException
     *             if the archive cannot be written
     */
    @Test
    void testUnzip( ) throws IOException
    {
        File target = new File( _directory, "target" );
        assertEquals( 0, _service.unzip( target, new ByteArrayInputStream( zip( "dir/a.txt", "../evil.txt", "accentué.txt", "dir/shell.jspx" ) ) ) );

        assertTrue( new File( target, "dir/a.txt" ).isFile( ) );
        assertFalse( new File( target, "dir/shell.jspx" ).exists( ) );
        assertEquals( 1, _service.unzip( target, new ByteArrayInputStream( zip( "dir/a.txt", "dir/b.txt" ) ) ) );
        assertFalse( new File( _directory, "evil.txt" ).exists( ) );
        assertFalse( new File( target, "accentué.txt" ).exists( ) );
        assertThrows( ZipException.class, ( ) -> _service.unzip( target, new ByteArrayInputStream( CONTENT ) ) );
    }

    /**
     * An archive holding more entries than allowed is refused
     *
     * @throws IOException
     *             if the archive cannot be written
     */
    @Test
    void testUnzipTooManyEntries( ) throws IOException
    {
        String [ ] entries = new String [ UploadService.MAX_ZIP_ENTRIES + 1];

        for ( int i = 0; i < entries.length; i++ )
        {
            entries [i] = "f" + i + ".txt";
        }

        byte [ ] archive = zip( entries );

        assertThrows( ZipException.class, ( ) -> _service.unzip( _directory, new ByteArrayInputStream( archive ) ) );
    }

    /**
     * The files are listed by name, a directory with the size of its content
     *
     * @throws IOException
     *             if the files cannot be written
     */
    @Test
    void testGetFiles( ) throws IOException
    {
        _service.unzip( _directory, new ByteArrayInputStream( zip( "B/x.txt", "B/y.txt", "a.txt" ) ) );

        List<UploadedFile> listFiles = _service.getFiles( _directory );

        assertEquals( 2, listFiles.size( ) );
        assertEquals( "a.txt", listFiles.get( 0 ).getName( ) );
        assertTrue( listFiles.get( 1 ).isDirectory( ) );
        assertEquals( 2L * CONTENT.length, listFiles.get( 1 ).getSize( ) );
        assertTrue( _service.getFiles( new File( _directory, "missing" ) ).isEmpty( ) );
    }

    /**
     * A directory is removed with its content, never out of the upload directory
     *
     * @throws IOException
     *             if the files cannot be written
     */
    @Test
    void testRemove( ) throws IOException
    {
        File upload = new File( _directory, "upload" );
        _service.unzip( upload, new ByteArrayInputStream( zip( "dir/sub/a.txt" ) ) );
        Files.write( new File( _directory, "kept.txt" ).toPath( ), CONTENT );

        assertFalse( _service.remove( upload, "../kept.txt" ) );
        assertTrue( new File( _directory, "kept.txt" ).exists( ) );
        assertFalse( _service.remove( upload, "missing" ) );
        assertTrue( _service.remove( upload, "dir" ) );
        assertFalse( new File( upload, "dir" ).exists( ) );
        assertTrue( upload.isDirectory( ) );
    }

    /**
     * Builds a zip archive
     *
     * @param entries
     *            the names of the entries
     * @return the archive
     * @throws IOException
     *             if the archive cannot be written
     */
    private static byte [ ] zip( String... entries ) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream( );

        try ( ZipOutputStream zipOut = new ZipOutputStream( out ) )
        {
            for ( String strEntry : entries )
            {
                zipOut.putNextEntry( new ZipEntry( strEntry ) );
                zipOut.write( CONTENT );
                zipOut.closeEntry( );
            }
        }

        return out.toByteArray( );
    }
}
