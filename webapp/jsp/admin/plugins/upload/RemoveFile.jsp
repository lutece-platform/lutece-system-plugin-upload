<%@ page errorPage="../../ErrorPage.jsp" %>

<jsp:useBean id="upload" scope="session" class="fr.paris.lutece.plugins.upload.web.UploadJspBean" />

<%
    upload.init( request, upload.RIGHT_MANAGE_UPLOAD ); 
    response.sendRedirect( upload.getConfirmRemoveFile( request ) );
%>