import { Datagrid, List, NumberField, TextField } from 'react-admin';

export function PortalAdminSchoolSnapshotsList() {
  return (
    <List
      perPage={25}
      resource="schoolSnapshots"
      title="教务缓存"
      sort={{ field: 'id', order: 'DESC' }}
    >
      <Datagrid bulkActionButtons={false} rowClick={false}>
        <TextField source="userId" label="用户 ID" />
        <TextField source="username" label="用户名" />
        <TextField source="email" label="邮箱" />
        <TextField source="studentId" label="学号" emptyText="-" />
        <TextField source="semester" label="学期" emptyText="-" />
        <NumberField source="scheduleCount" label="课表数" />
        <NumberField source="gradeCount" label="成绩数" />
      </Datagrid>
    </List>
  );
}
