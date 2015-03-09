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
package fr.paris.lutece.plugins.upload.web;

import fr.paris.lutece.plugins.upload.util.UploadUtil;
import fr.paris.lutece.portal.service.fileupload.FileUploadService;
import fr.paris.lutece.portal.service.message.AdminMessage;
import fr.paris.lutece.portal.service.message.AdminMessageService;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.web.admin.PluginAdminPageJspBean;
import fr.paris.lutece.portal.web.constants.Messages;
import fr.paris.lutece.portal.web.upload.MultipartHttpServletRequest;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.date.DateUtil;
import fr.paris.lutece.util.html.HtmlTemplate;
import fr.paris.lutece.util.html.Paginator;
import fr.paris.lutece.util.url.UrlItem;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.servlet.http.HttpServletRequest;


/**
 * This class provides the user interface to manage Library features ( manage, create, modify, remove, change order of
 * contact )
 */
public class UploadJspBean extends PluginAdminPageJspBean
{
    ////////////////////////////////////////////////////////////////////////////
    // Constants

    // Right
    public static final String RIGHT_MANAGE_UPLOAD = "UPLOAD_MANAGEMENT";

    // Templates
    private static final String TEMPLATE_MANAGE_UPLOAD = "admin/plugins/upload/manage_upload.html";
    private static final String TEMPLATE_FILE_ROW = "admin/plugins/upload/file_row.html";

    // JSP
    private static final String JSP_DO_REMOVE_FILE = "jsp/admin/plugins/upload/DoRemoveFile.jsp";

    // Parameters
    private static final String PARAMETER_FILE_NAME = "file_name";
    private static final String PARAMETER_DIRECTORY = "directory";
    private static final String PARAMETER_UNZIP = "unzip";

    // Properties
    private static final String PLUGIN_PARAM_DIRECTORY = "directory";
    private static final String PROPERTY_PAGE_TITLE_MANAGE_UPLOAD = "upload.manage_upload.pageTitle";
    private static final String PROPERTY_FILES_PER_PAGE = "paginator.upload.files.itemsPerPage";

    // Markers
    private static final String MARK_FILE_NAME = "file_name";
    private static final String MARK_FILE_SIZE = "file_size";
    private static final String MARK_FILE_DATE = "file_date";
    private static final String MARK_FILES_LIST = "files_list";
    private static final String MARK_DIRECTORY = "directory";
    private static final String MARK_DIRECTORY_LIST = "directory_list";
    private static final String MARK_PAGINATOR = "paginator";
    private static final String MARK_NB_ITEMS_PER_PAGE = "nb_items_per_page";

    // Message keys
    private static final String MESSAGE_CONFIRM_REMOVE_FILE = "upload.message.confirmRemoveFile";
    private static final String MESSAGE_FILE_EXISTS = "upload.message.fileExists";
    private static final String MESSAGE_ZIP_ERROR = "upload.message.zipError";

    //FileFilters used in the getDirectorySize method.
    private static final FileFilter dirFilter = new FileFilter(  )
        {
            public boolean accept( File pathname )
            {
                return pathname.isDirectory(  );
            }
        };

    private static final FileFilter fileFilter = new FileFilter(  )
        {
            public boolean accept( File pathname )
            {
                return ( !pathname.isDirectory(  ) );
            }
        };

    private int _nItemsPerPage;
    private int _nDefaultItemsPerPage;
    private String _strCurrentPageIndex;
    private Hashtable<String, String> _htDirectories = new Hashtable<String, String>(  );
    private ReferenceList _listDirectories = new ReferenceList(  );

    /**
     * Creates a new UploadJspBean object.
     */
    public UploadJspBean(  )
    {
    }

    /**
     * Returns the Files management form
     *
     * @param request The Http request
     * @return The Html form
     */
    public String getFiles( HttpServletRequest request )
    {
        setPageTitleProperty( PROPERTY_PAGE_TITLE_MANAGE_UPLOAD );

        String strPluginName = this.getPlugin(  ).getName(  );

        if ( strPluginName != null )
        {
            initDirectories( strPluginName );
        }

        String strDirectoryIndex = request.getParameter( PARAMETER_DIRECTORY );

        if ( ( strDirectoryIndex == null ) || ( strDirectoryIndex.equals( "" ) ) )
        {
            strDirectoryIndex = "1";
        }

        String strRelativeDirectory = getDirectory( strDirectoryIndex );
        String strDirectory = AppPathService.getWebAppPath(  ) + strRelativeDirectory;
        File directoryPictures = new File( strDirectory );
        File[] filePictures = directoryPictures.listFiles(  );

        // Creating names array
        int nFileNumber = filePictures.length;
        String[] arrayFile = new String[nFileNumber];

        for ( int i = 0; i < nFileNumber; i++ )
        {
            arrayFile[i] = filePictures[i].getName(  );
        }

        //defines the order of the files
        File[] screenArrayFile = new File[nFileNumber];
        String name;

        for ( int i = 0; i < nFileNumber; i++ )
        {
            int numero = i;
            name = arrayFile[i];

            for ( int a = 0; a < nFileNumber; a++ )
            {
                String strNameTemp = arrayFile[a];

                if ( name.toLowerCase(  ).trim(  ).compareTo( strNameTemp.toLowerCase(  ).trim(  ) ) > 0 )
                {
                    name = strNameTemp;
                    numero = a;
                }
            }

            screenArrayFile[i] = filePictures[numero];
            arrayFile[numero] = "{";
        }

        List<String> filesList = new ArrayList<String>(  );

        for ( int i = 0; i < nFileNumber; i++ )
        {
            File file = (File) screenArrayFile[i];
            HashMap modelFile = new HashMap(  );

            if ( file.isDirectory(  ) )
            {
                modelFile.put( MARK_FILE_NAME, file.getName(  ) + "\\" );

                // Getting the directory's size
                modelFile.put( MARK_FILE_SIZE, getDirectorySize( file ) );
            }
            else
            {
                modelFile.put( MARK_FILE_NAME, file.getName(  ) );

                // Getting the file's size
                modelFile.put( MARK_FILE_SIZE, file.length(  ) );
            }

            // Getting the file's date
            modelFile.put( MARK_FILE_DATE, DateUtil.getDateTimeString( file.lastModified(  ) ) );
            modelFile.put( MARK_DIRECTORY, strDirectoryIndex );

            HtmlTemplate templateFile = AppTemplateService.getTemplate( TEMPLATE_FILE_ROW, getLocale(  ), modelFile );

            //strRows.append( templateFile.getHtml(  ) );
            filesList.add( templateFile.getHtml(  ) );
        }

        // Paginator construction
        _strCurrentPageIndex = Paginator.getPageIndex( request, Paginator.PARAMETER_PAGE_INDEX, _strCurrentPageIndex );
        _nDefaultItemsPerPage = AppPropertiesService.getPropertyInt( PROPERTY_FILES_PER_PAGE, 10 );
        _nItemsPerPage = Paginator.getItemsPerPage( request, Paginator.PARAMETER_ITEMS_PER_PAGE, _nItemsPerPage,
                _nDefaultItemsPerPage );

        UrlItem url = new UrlItem( getHomeUrl( request ) );
        url.addParameter( PARAMETER_DIRECTORY, strDirectoryIndex );

        Paginator paginator = new Paginator( filesList, _nItemsPerPage, url.getUrl(  ), Paginator.PARAMETER_PAGE_INDEX,
                _strCurrentPageIndex );

        StringBuffer strRows = new StringBuffer(  );
        List<String> paginatedFilesList = paginator.getPageItems(  );

        for ( String fileRow : paginatedFilesList )
        {
            strRows.append( fileRow );
        }

        HashMap model = new HashMap(  );
        model.put( MARK_NB_ITEMS_PER_PAGE, "" + _nItemsPerPage );
        model.put( MARK_PAGINATOR, paginator );
        model.put( MARK_FILES_LIST, strRows.toString(  ) );
        model.put( MARK_DIRECTORY, strDirectoryIndex );
        model.put( MARK_DIRECTORY_LIST, _listDirectories );

        HtmlTemplate template = AppTemplateService.getTemplate( TEMPLATE_MANAGE_UPLOAD, getLocale(  ), model );

        return getAdminPage( template.getHtml(  ) );
    }

    /**
     * Initialize the directories for the plugin. Use the plugin configuration file
     * @param strPluginName The name of the plugin
     */
    private void initDirectories( String strPluginName )
    {
        // Reset lists
        _htDirectories.clear(  );
        _listDirectories.clear(  );

        // Gets plugin's parameters
        Plugin plugin = PluginService.getPlugin( strPluginName );
        Map<String, String> htParams = plugin.getParams(  );
        int i = 1;

        while ( true )
        {
            String strKey = PLUGIN_PARAM_DIRECTORY + i;
            String strDirectory = htParams.get( strKey );

            if ( strDirectory == null )
            {
                break;
            }

            _htDirectories.put( strKey, strDirectory );
            _listDirectories.addItem( i, strDirectory );
            i++;
        }
    }

    /**
     * Get the directory in the map odf the directories read in the plugin configuration file
     * @param strDirectoryIndex The index of Directory
     * @return The Key of The Directory Index
     */
    private String getDirectory( String strDirectoryIndex )
    {
        String strKey = PLUGIN_PARAM_DIRECTORY + strDirectoryIndex;

        return _htDirectories.get( strKey );
    }

    /**
     * Process file upload
     *
     * @param request Http request
     * @return Html form
     */
    public String doCreateFile( HttpServletRequest request )
    {
        String strDirectoryIndex = null;

        try
        {
            MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
            FileItem item = multiRequest.getFile( PARAMETER_FILE_NAME );

            // The index and value of the directory combo in the form
            strDirectoryIndex = multiRequest.getParameter( PARAMETER_DIRECTORY );

            String strRelativeDirectory = getDirectory( strDirectoryIndex );

            // The absolute path of the target directory
            String strDestDirectory = AppPathService.getWebAppPath(  ) + strRelativeDirectory;

            // List the files in the target directory
            File[] existingFiles = ( new File( strDestDirectory ) ).listFiles(  );

            // Is the 'unzip' checkbox checked?
            boolean bUnzip = ( multiRequest.getParameter( PARAMETER_UNZIP ) != null );

            if ( item != null && !item.getName( ).equals( StringUtils.EMPTY ) )
            {
                if ( !bUnzip ) // copy the file
                {
                    AppLogService.debug( "copying the file" );

                    // Getting downloadFile's name
                    String strNameFile = FileUploadService.getFileNameOnly( item );

                    // Clean name
                    String strClearName = UploadUtil.cleanFileName( strNameFile );

                    // Checking duplicate
                    if ( duplicate( strClearName, existingFiles ) )
                    {
                        return AdminMessageService.getMessageUrl( request, MESSAGE_FILE_EXISTS, AdminMessage.TYPE_STOP );
                    }

                    // Move the file to the target directory
                    File destFile = new File( strDestDirectory, strClearName );

                    FileOutputStream fos = new FileOutputStream( destFile );
                    fos.flush(  );
                    fos.write( item.get(  ) );
                    fos.close(  );
                }
                else // unzip the file
                {
                    AppLogService.debug( "unzipping the file" );

                    ZipFile zipFile;

                    try
                    {
                        // Create a temporary file with result of getTime() as unique file name
                        File tempFile = File.createTempFile( Long.toString( ( new Date(  ) ).getTime(  ) ), ".zip" );
                        // Delete temp file when program exits.
                        tempFile.deleteOnExit(  );

                        FileOutputStream fos = new FileOutputStream( tempFile );
                        fos.flush(  );
                        fos.write( item.get(  ) );
                        fos.close(  );
                        zipFile = new ZipFile( tempFile );
                    }
                    catch ( ZipException ze )
                    {
                        AppLogService.error( "Error opening zip file", ze );

                        return AdminMessageService.getMessageUrl( request, MESSAGE_ZIP_ERROR, AdminMessage.TYPE_STOP );
                    }

                    // Each zipped file is indentified by a zip entry :
                    Enumeration zipEntries = zipFile.entries(  );

                    while ( zipEntries.hasMoreElements(  ) )
                    {
                        ZipEntry zipEntry = (ZipEntry) zipEntries.nextElement(  );

                        // Clean the name :
                        String strZippedName = zipEntry.getName(  );
                        String strClearName = UploadUtil.cleanFilePath( strZippedName );

                        if ( strZippedName.equals( strClearName ) )
                        {
                            // The unzipped file :
                            File destFile = new File( strDestDirectory, strClearName );

                            // Create the parent directory structure if needed :
                            destFile.getParentFile(  ).mkdirs(  );

                            if ( !zipEntry.isDirectory(  ) ) // don't unzip directories
                            {
                                AppLogService.debug( "unzipping " + strZippedName + " to " + destFile.getName(  ) );

                                // InputStream from zipped data
                                InputStream inZipStream = zipFile.getInputStream( zipEntry );

                                // OutputStream to the destination file
                                OutputStream outDestStream = new FileOutputStream( destFile );

                                // Helper method to copy data
                                copyStream( inZipStream, outDestStream );

                                inZipStream.close(  );
                                outDestStream.close(  );
                            }
                            else
                            {
                                AppLogService.debug( "skipping directory " + strZippedName );
                            }
                        }
                        else
                        {
                            AppLogService.debug( "skipping accented file " + strZippedName );
                        }
                    }
                }
            }
            else
            {
                return AdminMessageService.getMessageUrl( request, Messages.MANDATORY_FIELDS, AdminMessage.TYPE_STOP );
            }
        }
        catch ( IOException e )
        {
            AppLogService.error( e.getMessage(  ), e );
        }

        // Returns upload management page
        UrlItem url = new UrlItem( getHomeUrl( request ) );

        if ( strDirectoryIndex != null )
        {
            url.addParameter( PARAMETER_DIRECTORY, strDirectoryIndex );
        }

        return url.getUrl(  );
    }

    /**
     * Check if file is already in library
     *
     * @param strFileName The name of the file to upload
     * @param repContent The name of the directory
     * @return The result (boolean)
     */
    private boolean duplicate( String strFileName, File[] repContent )
    {
        boolean duplicate = false;

        for ( int i = 0; i < repContent.length; i++ )
        {
            if ( repContent[i].getName(  ).equals( strFileName ) )
            {
                duplicate = true;
            }
        }

        return duplicate;
    }

    /**
     * Returns the confirmation to remove the file
     *
     * @param request The Http request
     * @return the confirmation page
     */
    public String getConfirmRemoveFile( HttpServletRequest request )
    {
        UrlItem url = new UrlItem( JSP_DO_REMOVE_FILE );
        url.addParameter( PARAMETER_DIRECTORY, request.getParameter( PARAMETER_DIRECTORY ) );
        url.addParameter( PARAMETER_FILE_NAME, request.getParameter( PARAMETER_FILE_NAME ) );

        return AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_FILE, url.getUrl(  ),
            AdminMessage.TYPE_CONFIRMATION );
    }

    /**
     * Process image's deleting
     *
     * @param request Http request
     * @return The library management page url
     */
    public String doRemoveFile( HttpServletRequest request )
    {
        String strDirectoryIndex = request.getParameter( PARAMETER_DIRECTORY );
        String strRelativeDirectory = getDirectory( strDirectoryIndex );
        String strDir = AppPathService.getWebAppPath(  ) + strRelativeDirectory;
        String strNameFile = request.getParameter( PARAMETER_FILE_NAME );

        // Deleting downloadFile physically
        File downloadFile = new File( strDir, strNameFile );

        if ( downloadFile.isDirectory(  ) )
        {
            deleteDirectory( downloadFile );
        }
        else
        {
            downloadFile.delete(  );
        }

        // Returns upload management page url
        UrlItem url = new UrlItem( getHomeUrl( request ) );

        if ( strDirectoryIndex != null )
        {
            url.addParameter( PARAMETER_DIRECTORY, strDirectoryIndex );
        }

        return url.getUrl(  );
    }

    /**
     * Copies data from an input stream to an output stream.
     * @param inStream The input stream
     * @param outStream The output stream
     * @throws IOException If an I/O error occurs
     */
    private static void copyStream( InputStream inStream, OutputStream outStream )
        throws IOException
    {
        BufferedInputStream inBufferedStream = new BufferedInputStream( inStream );
        BufferedOutputStream outBufferedStream = new BufferedOutputStream( outStream );

        int nByte = 0;

        while ( ( nByte = inBufferedStream.read(  ) ) > -1 )
        {
            outBufferedStream.write( nByte );
        }

        outBufferedStream.close(  );
        inBufferedStream.close(  );
    }

    /**
     * Returns the total size of a directory.
     * @param directory The directory
     * @return The total size
     */
    private static long getDirectorySize( File directory )
    {
        long lResult = 0;

        // We use a Stack (LIFO) to keep track of the unprocessed directories
        Stack<File> dirsToProcess = new Stack<File>(  );

        // The stack is initialized with the main directory
        dirsToProcess.push( directory );

        // Loop until all directories have been processed
        while ( !dirsToProcess.empty(  ) )
        {
            // Get a new directory from the stack
            File currentDir = dirsToProcess.pop(  );

            // Don't forget the directory's own size!
            lResult += currentDir.length(  );

            // Add the local files' size to the global size
            File[] files = currentDir.listFiles( fileFilter );

            for ( int i = 0; i < files.length; i++ )
            {
                lResult += files[i].length(  );
            }

            // Add the sub-directories to the stack
            File[] subDirs = currentDir.listFiles( dirFilter );

            for ( int i = 0; i < subDirs.length; i++ )
            {
                dirsToProcess.push( subDirs[i] );
            }
        }

        return lResult;
    }

    /**
     * Deletes a directory recursively.
     *
     * @param directory The directory to delete
     */
    private static void deleteDirectory( File directory )
    {
        // We use a Stack (LIFO) to keep track of the directories to delete
        Stack<File> dirsToDelete = new Stack<File>(  );

        // The stack is initialized with the main directory
        dirsToDelete.push( directory );

        // Loop until all directories have been deleted
        while ( !dirsToDelete.empty(  ) )
        {
            // Look at the directory on top of the stack (don't remove it!)
            File currentDir = (File) dirsToDelete.peek(  );

            // Are there any subdirectories?
            File[] subDirs = currentDir.listFiles( dirFilter );

            if ( subDirs.length > 0 )
            {
                // If so, add them to the stack
                for ( int i = 0; i < subDirs.length; i++ )
                {
                    dirsToDelete.push( subDirs[i] );
                }
            }
            else
            {
                // If not, delete all files in the directory
                File[] files = currentDir.listFiles( fileFilter );

                for ( int i = 0; i < files.length; i++ )
                {
                    files[i].delete(  );
                }

                // Then delete the directory
                currentDir.delete(  );

                // Then remove the directory from the stack
                dirsToDelete.pop(  );
            }
        }
    }
}
