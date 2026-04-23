const fs = require('fs');
const path = require('path');

const updateAdminHome = () => {
  const filePath = path.join(__dirname, 'src/pages/admin/AdminHome.tsx');
  let content = fs.readFileSync(filePath, 'utf8');
  content = content.replace(
    "import { Users, HardDrive, AlertTriangle, ArrowUpRight, FileText, Share2, Activity } from 'lucide-react';",
    "import { Users, HardDrive, AlertTriangle, ArrowUpRight, FileText, Share2, Activity } from 'lucide-react';\nimport { useAdminSummary } from '../../api/queries';"
  );
  content = content.replace(
    'const AdminHome: React.FC = () => {',
    'const AdminHome: React.FC = () => {\n  const { data, isLoading, isError } = useAdminSummary();'
  );
  content = content.replace(
    '<div className="grid grid-cols-1 lg:grid-cols-4 gap-6">',
    '{isLoading ? (\n        <div className="p-8 text-center text-text-muted-light">加载中...</div>\n      ) : isError ? (\n        <div className="p-8 text-center text-red-500">加载失败</div>\n      ) : (\n      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">'
  );
  content = content.replace('1,284', '{data?.user_total ?? 0}');
  content = content.replace('45,210', '{data?.file_total ?? 0}');
  content = content.replace('3,492', '{data?.share_total ?? 0}');
  content = content.replace("84%", "{data?.storage_usage_percentage ?? 0}%");
  content = content.replace("style={{ width: '84%' }}", "style={{ width: `${data?.storage_usage_percentage ?? 0}%` }}");
  content = content.replace(
    '</AdminLayout>',
    '      )}\n    </AdminLayout>'
  );
  fs.writeFileSync(filePath, content);
};

const processListPage = (filePath, hookName, mapVarName, fieldsMapping) => {
  let content = fs.readFileSync(filePath, 'utf8');
  
  // 1. Add hook import
  const importRegex = /import React(?:, { useState })? from 'react';/;
  if (content.includes('useState')) {
    // already has useState
  } else {
    content = content.replace("import React from 'react';", "import React, { useState } from 'react';");
  }
  content = content.replace(
    "import AdminLayout from '../../components/AdminLayout';",
    `import AdminLayout from '../../components/AdminLayout';\nimport { ${hookName} } from '../../api/queries';`
  );

  // 2. Add state and hook
  const componentMatch = content.match(/const \w+: React\.FC = \(\) => {/);
  if (componentMatch) {
    const states = `\n  const [page, setPage] = useState(1);\n  const [pageSize, setPageSize] = useState(10);\n  const { data, isLoading, isError } = ${hookName}({ page, page_size: pageSize });\n`;
    content = content.replace(componentMatch[0], componentMatch[0] + states);
  }

  // 3. Replace mock data with hook data and add loading boundary
  // Find the tbody mapping
  const tbodyRegex = /<tbody>\s*\{\[\s*\{[\s\S]*?\}\s*\]\.map\(\([a-zA-Z]+\) => \(/;
  const tbodyMatch = content.match(tbodyRegex);
  
  if (tbodyMatch) {
    // The part to replace is `{[...].map((app) => (`
    const mapMatch = content.match(/\{\[\s*\{[\s\S]*?\}\s*\]\.map\(\([a-zA-Z]+\) => \(/);
    if (mapMatch) {
      const varNameMatch = mapMatch[0].match(/\(\(([a-zA-Z]+)\) => \(/);
      const varName = varNameMatch ? varNameMatch[1] : mapVarName;
      
      content = content.replace(mapMatch[0], `{(data?.items || []).map((${varName}: any) => (`);
      
      // Before <div className="overflow-x-auto"> add the loading state
      content = content.replace(
        '<div className="overflow-x-auto">',
        `{isLoading ? (\n          <div className="p-8 text-center text-text-muted-light">加载中...</div>\n        ) : isError ? (\n          <div className="p-8 text-center text-red-500">加载失败</div>\n        ) : (\n        <div className="overflow-x-auto">`
      );
      
      // After </table>... wait, AdminUser has pagination. So we need to wrap the whole table or the card container contents.
      // Let's just wrap around overflow-x-auto and any pagination below it.
      // Easiest is to find the closing </div> of `overflow-x-auto` or `card-container`.
      // Actually, if we wrap inside `card-container`:
      content = content.replace(
        /<div className="card-container animate-fade-in-up"([^>]*)>([\s\S]*?)<\/AdminLayout>/,
        (match, p1, p2) => {
          // p2 is the content inside card-container and any subsequent nodes up to </AdminLayout>
          // wait, card-container has its own closing div. Let's do string manipulation.
          return match; // fallback
        }
      );
    }
  }

  // Use a safer string replacement for loading state
  // We'll wrap the contents of the `card-container animate-fade-in-up` that contains the table.
  const tableContainerStart = content.indexOf('<div className="card-container animate-fade-in-up"');
  if (tableContainerStart !== -1) {
    // replace inside it...
  }

  return content;
};

// Let's do it file by file with specialized replacements to ensure exact matches.
