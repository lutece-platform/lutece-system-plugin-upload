-- liquibase formatted sql
-- changeset upload:init_core_upload.sql
-- preconditions onFail:MARK_RAN onError:WARN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM core_admin_right WHERE id_right = 'UPLOAD_MANAGEMENT'
--
-- Dumping data for table core_admin_right
--
INSERT INTO core_admin_right (id_right,name,level_right,admin_url,description,is_updatable,plugin_name,id_feature_group,icon_url,documentation_url) VALUES 
('UPLOAD_MANAGEMENT','upload.adminFeature.upload_management.name',3,'jsp/admin/plugins/upload/ManageUpload.jsp','upload.adminFeature.upload_management.description',0,'upload','CONTENT','ti ti-upload', NULL);


--
-- Dumping data for table core_user_right
--
INSERT INTO core_user_right (id_right,id_user) VALUES ('UPLOAD_MANAGEMENT',1);
