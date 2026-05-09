ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(64);
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(16);
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS preferred_theme VARCHAR(16);
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS disable_view_sync BOOLEAN;
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS default_open_with_by_ext TEXT;
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS role VARCHAR(32);
ALTER TABLE portal_user ADD COLUMN IF NOT EXISTS banned BOOLEAN;
ALTER TABLE portal_file ADD COLUMN IF NOT EXISTS is_recycle_root BOOLEAN;
ALTER TABLE IF EXISTS portal_background_task ALTER COLUMN task_type ENUM(
    'ARCHIVE',
    'EXTRACT',
    'SEARCH_INDEX_REBUILD',
    'STORAGE_POLICY_MIGRATION',
    'THUMBNAIL',
    'MEDIA_META',
    'WORKSPACE_MUTATION',
    'REMOTE_DOWNLOAD',
    'HLS_TRANSCODE',
    'CLEANUP'
);

UPDATE portal_user
SET display_name = username
WHERE display_name IS NULL OR TRIM(display_name) = '';

UPDATE portal_user
SET preferred_language = 'zh-CN'
WHERE preferred_language IS NULL OR TRIM(preferred_language) = '';

UPDATE portal_user
SET preferred_theme = 'system'
WHERE preferred_theme IS NULL OR TRIM(preferred_theme) = '';

UPDATE portal_user
SET disable_view_sync = FALSE
WHERE disable_view_sync IS NULL;

UPDATE portal_user
SET role = 'USER'
WHERE role IS NULL OR TRIM(role) = '';

UPDATE portal_user
SET banned = FALSE
WHERE banned IS NULL;

UPDATE portal_file
SET is_recycle_root = FALSE
WHERE is_recycle_root IS NULL;
