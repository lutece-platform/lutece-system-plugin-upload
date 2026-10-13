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

import fr.paris.lutece.plugins.upload.business.UploadedFile;
import fr.paris.lutece.plugins.upload.service.UploadService;
import fr.paris.lutece.portal.service.fileupload.FileUploadService;
import fr.paris.lutece.portal.service.i18n.I18nService;
import fr.paris.lutece.portal.service.message.AdminMessage;
import fr.paris.lutece.portal.service.message.AdminMessageService;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.upload.MultipartItem;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.util.mvc.admin.MVCAdminJspBean;
import fr.paris.lutece.portal.util.mvc.admin.annotations.Controller;
import fr.paris.lutece.portal.util.mvc.commons.annotations.Action;
import fr.paris.lutece.portal.util.mvc.commons.annotations.View;
import fr.paris.lutece.portal.util.mvc.utils.MVCUtils;
import fr.paris.lutece.portal.web.cdi.mvc.Models;
import fr.paris.lutece.portal.web.constants.Messages;
import fr.paris.lutece.portal.web.upload.MultipartHttpServletRequest;
import fr.paris.lutece.portal.web.util.IPager;
import fr.paris.lutece.portal.web.util.Pager;
import fr.paris.lutece.util.url.UrlItem;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.util.Map;
import java.util.zip.ZipException;

/**
 * Manages the files of the upload directories
 */
@RequestScoped
@Named( "uploadFileJspBean" )
@Controller( controllerJsp = UploadJspBean.CONTROLLER_JSP, controllerPath = UploadJspBean.CONTROLLER_PATH, right = UploadJspBean.RIGHT_MANAGE_UPLOAD, securityTokenEnabled = true )
public class UploadJspBean extends MVCAdminJspBean
{
    /** The right of the feature */
    public static final String RIGHT_MANAGE_UPLOAD = "UPLOAD_MANAGEMENT";
    /** The controller JSP */
    public static final String CONTROLLER_JSP = "ManageUpload.jsp";
    /** The controller path */
    public static final String CONTROLLER_PATH = "jsp/admin/plugins/upload/";

    private static final long serialVersionUID = 4113316366416431212L;

    private static final String TEMPLATE_MANAGE_UPLOAD = "admin/plugins/upload/manage_upload.html";

    private static final String VIEW_MANAGE_FILES = "manageFiles";
    private static final String VIEW_CONFIRM_REMOVE_FILE = "confirmRemoveFile";
    private static final String ACTION_CREATE_FILE = "createFile";
    private static final String ACTION_REMOVE_FILE = "removeFile";

    private static final String PARAMETER_FILE_NAME = "file_name";
    private static final String PARAMETER_DIRECTORY = "directory";
    private static final String PARAMETER_UNZIP = "unzip";
    private static final String DEFAULT_DIRECTORY = "1";

    private static final String PROPERTY_PAGE_TITLE_MANAGE_UPLOAD = "upload.manage_upload.pageTitle";
    private static final String PROPERTY_FILES_PER_PAGE = "paginator.upload.files.itemsPerPage";

    private static final String MARK_FILES_LIST = "files_list";
    private static final String MARK_DIRECTORY = "directory";
    private static final String MARK_DIRECTORY_LIST = "directory_list";

    private static final String MESSAGE_CONFIRM_REMOVE_FILE = "upload.message.confirmRemoveFile";
    private static final String MESSAGE_FILE_EXISTS = "upload.message.fileExists";
    private static final String MESSAGE_FILE_NOT_FOUND = "upload.message.fileNotFound";
    private static final String MESSAGE_UNKNOWN_DIRECTORY = "upload.message.unknownDirectory";
    private static final String MESSAGE_ZIP_ERROR = "upload.message.zipError";
    private static final String MESSAGE_WRITE_ERROR = "upload.message.writeError";
    private static final String MESSAGE_FORBIDDEN_TYPE = "upload.message.forbiddenType";
    private static final String WARNING_FILES_REPLACED = "upload.warning.filesReplaced";
    private static final String INFO_FILE_CREATED = "upload.info.fileCreated";
    private static final String INFO_ARCHIVE_EXTRACTED = "upload.info.archiveExtracted";
    private static final String INFO_FILE_REMOVED = "upload.info.fileRemoved";

    @Inject
    @Pager( name = "upload.files", listBookmark = MARK_FILES_LIST, defaultItemsPerPage = PROPERTY_FILES_PER_PAGE, baseUrl = CONTROLLER_PATH + CONTROLLER_JSP )
    private IPager<UploadedFile, UploadedFile> _pager;

    @Inject
    private UploadService _uploadService;

    /**
     * Returns the plugin of this feature
     *
     * @return the upload plugin
     */
    @Override
    public Plugin getPlugin( )
    {
        return PluginService.getPlugin( UploadService.PLUGIN_NAME );
    }

    /**
     * Displays the files of the current directory and the upload form
     *
     * @param request
     *            the HTTP request
     * @param model
     *            the model
     * @return the page
     */
    @View( value = VIEW_MANAGE_FILES, defaultView = true, securityTokenAction = ACTION_CREATE_FILE )
    public String getManageFiles( HttpServletRequest request, Models model )
    {
        String strDirectoryIndex = getDirectoryIndex( request );
        File directory = _uploadService.getDirectory( strDirectoryIndex );

        if ( directory == null )
        {
            return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_UNKNOWN_DIRECTORY, AdminMessage.TYPE_STOP ) );
        }

        UrlItem urlPager = new UrlItem( CONTROLLER_PATH + CONTROLLER_JSP );
        urlPager.addParameter( MVCUtils.PARAMETER_VIEW, VIEW_MANAGE_FILES );
        urlPager.addParameter( PARAMETER_DIRECTORY, strDirectoryIndex );

        _pager.withBaseUrl( urlPager.getUrl( ) ).withListItem( _uploadService.getFiles( directory ) ).populateModels( request, model, getLocale( ) );

        model.put( MARK_DIRECTORY, strDirectoryIndex );
        model.put( MARK_DIRECTORY_LIST, _uploadService.getDirectories( ) );

        return getPage( PROPERTY_PAGE_TITLE_MANAGE_UPLOAD, TEMPLATE_MANAGE_UPLOAD, model );
    }

    /**
     * Writes the uploaded file in the current directory, or extracts it there when it is a zip archive to unzip, warning about the files the archive replaced
     *
     * @param request
     *            the HTTP request
     * @return the redirection to the files of the directory
     */
    @Action( ACTION_CREATE_FILE )
    public String doCreateFile( HttpServletRequest request )
    {
        String strDirectoryIndex = getDirectoryIndex( request );
        File directory = _uploadService.getDirectory( strDirectoryIndex );
        MultipartItem item = ( request instanceof MultipartHttpServletRequest multipartRequest ) ? multipartRequest.getFile( PARAMETER_FILE_NAME ) : null;

        if ( directory == null )
        {
            return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_UNKNOWN_DIRECTORY, AdminMessage.TYPE_STOP ) );
        }

        if ( item == null || StringUtils.isEmpty( item.getName( ) ) )
        {
            addError( Messages.MANDATORY_FIELDS, getLocale( ) );

            return redirectManageFiles( request, strDirectoryIndex );
        }

        try ( InputStream inputStream = item.getInputStream( ) )
        {
            if ( request.getParameter( PARAMETER_UNZIP ) != null )
            {
                int nReplaced = _uploadService.unzip( directory, inputStream );

                if ( nReplaced > 0 )
                {
                    addWarning( I18nService.getLocalizedString( WARNING_FILES_REPLACED, new Object [ ] {
                            nReplaced
                    }, getLocale( ) ) );
                }

                addInfo( INFO_ARCHIVE_EXTRACTED, getLocale( ) );
            }
            else
            {
                _uploadService.saveFile( directory, FileUploadService.getFileNameOnly( item ), inputStream );
                addInfo( INFO_FILE_CREATED, getLocale( ) );
            }
        }
        catch( FileAlreadyExistsException e )
        {
            addError( MESSAGE_FILE_EXISTS, getLocale( ) );
        }
        catch( AccessDeniedException e )
        {
            addError( MESSAGE_FORBIDDEN_TYPE, getLocale( ) );
        }
        catch( ZipException e )
        {
            AppLogService.error( "Error opening zip file", e );
            addError( MESSAGE_ZIP_ERROR, getLocale( ) );
        }
        catch( IOException e )
        {
            AppLogService.error( e.getMessage( ), e );
            addError( MESSAGE_WRITE_ERROR, getLocale( ) );
        }

        return redirectManageFiles( request, strDirectoryIndex );
    }

    /**
     * Asks the confirmation of the removal of a file
     *
     * @param request
     *            the HTTP request
     * @return the redirection to the confirmation message
     */
    @View( value = VIEW_CONFIRM_REMOVE_FILE, securityTokenAction = ACTION_REMOVE_FILE )
    public String getConfirmRemoveFile( HttpServletRequest request )
    {
        String strDirectoryIndex = getDirectoryIndex( request );
        String strFileName = getFileName( request );
        File directory = _uploadService.getDirectory( strDirectoryIndex );

        if ( directory == null || _uploadService.resolve( directory, strFileName ) == null )
        {
            return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_FILE_NOT_FOUND, AdminMessage.TYPE_STOP ) );
        }

        UrlItem url = new UrlItem( getActionUrl( ACTION_REMOVE_FILE ) );
        url.addParameter( PARAMETER_DIRECTORY, strDirectoryIndex );
        url.addParameter( PARAMETER_FILE_NAME, strFileName );

        return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_CONFIRM_REMOVE_FILE, new Object [ ] {
                strFileName
        }, url.getUrl( ), AdminMessage.TYPE_CONFIRMATION ) );
    }

    /**
     * Removes a file or a directory with its content
     *
     * @param request
     *            the HTTP request
     * @return the redirection to the files of the directory
     */
    @Action( ACTION_REMOVE_FILE )
    public String doRemoveFile( HttpServletRequest request )
    {
        String strDirectoryIndex = getDirectoryIndex( request );
        File directory = _uploadService.getDirectory( strDirectoryIndex );

        if ( directory == null )
        {
            return redirect( request, AdminMessageService.getMessageUrl( request, MESSAGE_UNKNOWN_DIRECTORY, AdminMessage.TYPE_STOP ) );
        }

        try
        {
            if ( _uploadService.remove( directory, getFileName( request ) ) )
            {
                addInfo( INFO_FILE_REMOVED, getLocale( ) );
            }
            else
            {
                addError( MESSAGE_FILE_NOT_FOUND, getLocale( ) );
            }
        }
        catch( IOException e )
        {
            AppLogService.error( e.getMessage( ), e );
            addError( MESSAGE_WRITE_ERROR, getLocale( ) );
        }

        return redirectManageFiles( request, strDirectoryIndex );
    }

    /**
     * Returns the index of the directory the request names
     *
     * @param request
     *            the HTTP request
     * @return the index, the first directory when the request names none
     */
    private static String getDirectoryIndex( HttpServletRequest request )
    {
        return StringUtils.defaultIfEmpty( request.getParameter( PARAMETER_DIRECTORY ), DEFAULT_DIRECTORY );
    }

    /**
     * Returns the name of the file the request names, as typed
     *
     * @param request
     *            the HTTP request
     * @return the file name, null when the request names none
     */
    private static String getFileName( HttpServletRequest request )
    {
        return StringEscapeUtils.unescapeHtml4( request.getParameter( PARAMETER_FILE_NAME ) );
    }

    /**
     * Redirects to the files of a directory
     *
     * @param request
     *            the HTTP request
     * @param strDirectoryIndex
     *            the index of the directory
     * @return the redirection
     */
    private String redirectManageFiles( HttpServletRequest request, String strDirectoryIndex )
    {
        return redirect( request, VIEW_MANAGE_FILES, Map.of( PARAMETER_DIRECTORY, strDirectoryIndex ) );
    }
}
