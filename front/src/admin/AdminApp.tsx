import FolderOutlined from '@mui/icons-material/FolderOutlined';
import GroupsOutlined from '@mui/icons-material/GroupsOutlined';
import SchoolOutlined from '@mui/icons-material/SchoolOutlined';
import { Admin, Resource } from 'react-admin';

import { portalAdminAuthProvider } from './auth-provider';
import { portalAdminDataProvider } from './data-provider';
import { PortalAdminDashboard } from './dashboard';
import { PortalAdminFilesList } from './files-list';
import { PortalAdminUsersList } from './users-list';
import { PortalAdminSchoolSnapshotsList } from './school-snapshots-list';

export default function PortalAdminApp() {
  return (
    <Admin
      authProvider={portalAdminAuthProvider}
      basename="/admin"
      dashboard={PortalAdminDashboard}
      dataProvider={portalAdminDataProvider}
      disableTelemetry
      requireAuth
      title="YOYUZH Admin"
    >
      <Resource
        name="users"
        icon={GroupsOutlined}
        list={PortalAdminUsersList}
        options={{ label: '用户资源' }}
        recordRepresentation="username"
      />
      <Resource
        name="files"
        icon={FolderOutlined}
        list={PortalAdminFilesList}
        options={{ label: '文件资源' }}
        recordRepresentation="filename"
      />
      <Resource
        name="schoolSnapshots"
        icon={SchoolOutlined}
        list={PortalAdminSchoolSnapshotsList}
        options={{ label: '教务缓存' }}
        recordRepresentation="username"
      />
    </Admin>
  );
}
