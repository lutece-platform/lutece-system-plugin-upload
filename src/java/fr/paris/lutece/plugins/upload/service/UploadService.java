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

import fr.paris.lutece.plugins.upload.business.UploadedFile;
import fr.paris.lutece.plugins.upload.util.UploadUtil;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.util.ReferenceList;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Manages the files of the upload directories declared by the plugin parameters
 */
@ApplicationScoped
public class UploadService
{
    /** The plugin name */
    public static final String PLUGIN_NAME = "upload";

    /** The maximum number of entries of an archive */
    public static final int MAX_ZIP_ENTRIES = 10000;
    /** The maximum size of the files extracted from an archive, in bytes */
    public static final long MAX_ZIP_SIZE = 500L * 1024 * 1024;

    private static final String PLUGIN_PARAM_DIRECTORY = "directory";
    private static final Set<String> DENIED_EXTENSIONS = Set.of( "jsp", "jspx", "jspf" );
    private static final int BUFFER_SIZE = 8192;

    /**
     * Returns the upload directories, coded by their index
     *
     * @return the directories
     */
    public ReferenceList getDirectories( )
    {
        ReferenceList listDirectories = new ReferenceList( );
        Map<String, String> mapParams = getPluginParams( );

        for ( int i = 1; mapParams.get( PLUGIN_PARAM_DIRECTORY + i ) != null; i++ )
        {
            listDirectories.addItem( i, mapParams.get( PLUGIN_PARAM_DIRECTORY + i ) );
        }

        return listDirectories;
    }

    /**
     * Returns the upload directory of an index
     *
     * @param strDirectoryIndex
     *            the index of the directory
     * @return the directory, null when no directory has this index
     */
    public File getDirectory( String strDirectoryIndex )
    {
        String strRelativeDirectory = getPluginParams( ).get( PLUGIN_PARAM_DIRECTORY + strDirectoryIndex );

        if ( strRelativeDirectory == null )
        {
            return null;
        }

        return new File( AppPathService.getWebAppPath( ), strRelativeDirectory );
    }

    /**
     * Returns the content of a directory, sorted by name
     *
     * @param directory
     *            the directory
     * @return the files and directories it holds, empty when it does not exist
     */
    public List<UploadedFile> getFiles( File directory )
    {
        File [ ] files = directory.listFiles( );

        if ( files == null )
        {
            return Collections.emptyList( );
        }

        List<UploadedFile> listFiles = new ArrayList<>( files.length );
        Arrays.sort( files, Comparator.comparing( ( File file ) -> file.getName( ).trim( ).toLowerCase( Locale.ROOT ) ) );

        for ( File file : files )
        {
            long lSize = file.isDirectory( ) ? getDirectorySize( file.toPath( ) ) : file.length( );
            listFiles.add( new UploadedFile( file.getName( ), lSize, file.lastModified( ), file.isDirectory( ) ) );
        }

        return listFiles;
    }

    /**
     * Writes a file in a directory, under its cleaned name
     *
     * @param directory
     *            the directory
     * @param strFileName
     *            the name of the uploaded file
     * @param inputStream
     *            the content
     * @return the name the file was written under
     * @throws FileAlreadyExistsException
     *             if the directory already holds a file of that name
     * @throws AccessDeniedException
     *             if the file is of a type the server would execute
     * @throws IOException
     *             if the file cannot be written
     */
    public String saveFile( File directory, String strFileName, InputStream inputStream ) throws IOException
    {
        String strCleanName = UploadUtil.cleanFileName( strFileName );
        File file = resolve( directory, strCleanName );

        if ( file == null )
        {
            throw new IOException( "Invalid file name: " + strFileName );
        }

        if ( isDenied( strCleanName ) )
        {
            throw new AccessDeniedException( strCleanName );
        }

        if ( file.exists( ) )
        {
            throw new FileAlreadyExistsException( strCleanName );
        }

        Files.createDirectories( directory.toPath( ) );
        Files.copy( inputStream, file.toPath( ) );

        return strCleanName;
    }

    /**
     * Extracts a zip archive in a directory, replacing the files of the same name. Directories, entries leading out of the directory, entries of a type the
     * server would execute and entries whose name holds a character other than a letter, a digit, a dot, a dash or a slash are skipped.
     *
     * @param directory
     *            the directory
     * @param inputStream
     *            the archive
     * @return the number of existing files the archive replaced
     * @throws ZipException
     *             if the content is not a zip archive, holds no entry, or more entries or bytes than allowed
     * @throws IOException
     *             if a file cannot be written
     */
    public int unzip( File directory, InputStream inputStream ) throws IOException
    {
        int nReplaced = 0;
        int nEntries = 0;
        long [ ] remaining = {
                MAX_ZIP_SIZE
        };

        try ( ZipInputStream zipInputStream = new ZipInputStream( inputStream ) )
        {
            for ( ZipEntry zipEntry = zipInputStream.getNextEntry( ); zipEntry != null; zipEntry = zipInputStream.getNextEntry( ) )
            {
                nEntries++;

                if ( nEntries > MAX_ZIP_ENTRIES )
                {
                    throw new ZipException( "Too many entries in the archive" );
                }

                if ( extractEntry( directory, zipEntry, zipInputStream, remaining ) )
                {
                    nReplaced++;
                }
            }
        }

        if ( nEntries == 0 )
        {
            throw new ZipException( "No entry in the archive" );
        }

        return nReplaced;
    }

    /**
     * Writes one entry of an archive
     *
     * @param directory
     *            the target directory
     * @param zipEntry
     *            the entry
     * @param zipInputStream
     *            the archive, positioned on the entry
     * @param remaining
     *            the number of bytes the archive may still write, decreased by this entry
     * @return true when the entry replaced an existing file
     * @throws IOException
     *             if the file cannot be written, or the archive exceeds the allowed size
     */
    private boolean extractEntry( File directory, ZipEntry zipEntry, ZipInputStream zipInputStream, long [ ] remaining ) throws IOException
    {
        String strEntryName = zipEntry.getName( );
        File file = resolve( directory, strEntryName );

        if ( zipEntry.isDirectory( ) || !strEntryName.equals( UploadUtil.cleanFilePath( strEntryName ) ) || file == null || isDenied( strEntryName ) )
        {
            AppLogService.debug( "Skipping the zip entry {}", strEntryName );

            return false;
        }

        boolean bReplaced = file.exists( );
        Files.createDirectories( file.toPath( ).getParent( ) );

        try ( OutputStream outputStream = Files.newOutputStream( file.toPath( ) ) )
        {
            byte [ ] buffer = new byte [ BUFFER_SIZE];

            for ( int nRead = zipInputStream.read( buffer ); nRead > 0; nRead = zipInputStream.read( buffer ) )
            {
                remaining [0] -= nRead;

                if ( remaining [0] < 0 )
                {
                    throw new ZipException( "The archive exceeds the allowed size" );
                }

                outputStream.write( buffer, 0, nRead );
            }
        }
        catch( ZipException e )
        {
            Files.deleteIfExists( file.toPath( ) );

            throw e;
        }

        return bReplaced;
    }

    /**
     * Tells whether a file is of a type the server would execute
     *
     * @param strName
     *            the file name
     * @return true when the file must not be written in the webapp
     */
    private static boolean isDenied( String strName )
    {
        int nDot = strName.lastIndexOf( '.' );

        return nDot >= 0 && DENIED_EXTENSIONS.contains( strName.substring( nDot + 1 ).toLowerCase( Locale.ROOT ) );
    }

    /**
     * Removes a file or a directory with its content
     *
     * @param directory
     *            the upload directory
     * @param strFileName
     *            the name of the file in that directory
     * @return false when the name leads out of the directory or names nothing
     * @throws IOException
     *             if a file cannot be deleted
     */
    public boolean remove( File directory, String strFileName ) throws IOException
    {
        File file = resolve( directory, strFileName );

        if ( file == null || !file.exists( ) )
        {
            return false;
        }

        try ( Stream<Path> paths = Files.walk( file.toPath( ) ) )
        {
            for ( Path path : paths.sorted( Comparator.reverseOrder( ) ).toList( ) )
            {
                Files.delete( path );
            }
        }

        return true;
    }

    /**
     * Resolves a name inside a directory
     *
     * @param directory
     *            the directory
     * @param strName
     *            the relative name
     * @return the file, null when the name is empty or leads out of the directory
     */
    public File resolve( File directory, String strName )
    {
        if ( strName == null || strName.isBlank( ) )
        {
            return null;
        }

        Path root = directory.toPath( ).toAbsolutePath( ).normalize( );
        Path path = root.resolve( strName ).normalize( );

        if ( !path.startsWith( root ) || path.equals( root ) )
        {
            return null;
        }

        return path.toFile( );
    }

    /**
     * Returns the size of the files a directory holds, at any depth
     *
     * @param directory
     *            the directory
     * @return the size in bytes
     */
    private static long getDirectorySize( Path directory )
    {
        try ( Stream<Path> paths = Files.walk( directory ) )
        {
            return paths.filter( Files::isRegularFile ).mapToLong( UploadService::getFileSize ).sum( );
        }
        catch ( IOException | UncheckedIOException e )
        {
            AppLogService.error( "Unable to read the directory {}", directory, e );

            return 0;
        }
    }

    /**
     * Returns the size of a file
     *
     * @param path
     *            the file
     * @return the size in bytes
     */
    private static long getFileSize( Path path )
    {
        return path.toFile( ).length( );
    }

    /**
     * Returns the parameters of the plugin
     *
     * @return the parameters, empty when the plugin is not loaded
     */
    private static Map<String, String> getPluginParams( )
    {
        Plugin plugin = PluginService.getPlugin( PLUGIN_NAME );

        return plugin == null ? Collections.emptyMap( ) : plugin.getParams( );
    }
}
