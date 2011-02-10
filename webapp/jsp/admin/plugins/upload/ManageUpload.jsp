<%@ page errorPage="../../ErrorPage.jsp" %>
<jsp:include page="../../AdminHeader.jsp" />

<jsp:useBean id="upload" scope="session" class="fr.paris.lutece.plugins.upload.web.UploadJspBean" />

<% upload.init( request, upload.RIGHT_MANAGE_UPLOAD ); %>
<%= upload.getFiles( request ) %>

<%@ include file="../../AdminFooter.jsp" %>