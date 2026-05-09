import React from 'react';
import {
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { flexRender, getCoreRowModel, useReactTable, type ColumnDef } from '@tanstack/react-table';

export type AdminColumn<T> = {
  id: string;
  header: string;
  accessor: (row: T) => React.ReactNode;
  className?: string;
};

export type AdminDataTableProps<T> = {
  rows: T[];
  columns: AdminColumn<T>[];
  getRowKey: (row: T) => string | number;
  emptyText?: string;
};

function AdminDataTable<T>({
  rows,
  columns,
  getRowKey,
  emptyText = '暂无数据',
}: AdminDataTableProps<T>) {
  const tableColumns = React.useMemo<ColumnDef<T>[]>(
    () =>
      columns.map((column) => ({
        id: column.id,
        header: column.header,
        cell: (context) => column.accessor(context.row.original),
        meta: {
          className: column.className,
        },
      })),
    [columns],
  );

  const table = useReactTable<T>({
    data: rows,
    columns: tableColumns,
    getCoreRowModel: getCoreRowModel(),
    getRowId: (row) => String(getRowKey(row)),
  });

  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: 3,
        bgcolor: 'background.paper',
      }}
    >
      <Table size="small" sx={{ minWidth: 640 }}>
        <TableHead>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id}>
              {headerGroup.headers.map((header) => {
                const headerMeta = header.column.columnDef.meta as { className?: string } | undefined;

                return (
                  <TableCell
                    key={header.id}
                    className={headerMeta?.className}
                    sx={{
                      py: 1.25,
                      px: 2,
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      textTransform: 'uppercase',
                      letterSpacing: '0.06em',
                      color: 'text.secondary',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableHead>
        <TableBody>
          {table.getRowModel().rows.length > 0 ? (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id} hover>
                {row.getVisibleCells().map((cell) => {
                  const cellMeta = cell.column.columnDef.meta as { className?: string } | undefined;

                  return (
                    <TableCell
                      key={cell.id}
                      className={cellMeta?.className}
                      sx={{
                        px: 2,
                        py: 1.5,
                        fontSize: '0.875rem',
                        verticalAlign: 'middle',
                        borderBottomColor: 'divider',
                      }}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} sx={{ py: 8, textAlign: 'center' }}>
                <Typography variant="body2" color="text.secondary">
                  {emptyText}
                </Typography>
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export default AdminDataTable;
