import {
  BlockTypeSelect,
  BoldItalicUnderlineToggles,
  CodeToggle,
  CreateLink,
  DiffSourceToggleWrapper,
  InsertCodeBlock,
  InsertTable,
  InsertThematicBreak,
  ListsToggle,
  MDXEditor,
  Separator,
  UndoRedo,
  codeBlockPlugin,
  codeMirrorPlugin,
  diffSourcePlugin,
  headingsPlugin,
  linkDialogPlugin,
  linkPlugin,
  listsPlugin,
  markdownShortcutPlugin,
  quotePlugin,
  tablePlugin,
  thematicBreakPlugin,
  toolbarPlugin,
} from '@mdxeditor/editor';
import '@mdxeditor/editor/style.css';
import { Box } from '@mui/material';

export interface MarkdownDocumentEditorProps {
  value: string;
  initialValue: string;
  readOnly?: boolean;
  darkMode?: boolean;
  onChange: (value: string) => void;
  onSaveShortcut?: () => void;
}

export default function MarkdownDocumentEditor({
  value,
  initialValue,
  readOnly = false,
  darkMode = false,
  onChange,
  onSaveShortcut,
}: MarkdownDocumentEditorProps) {
  return (
    <Box
      sx={{
        minHeight: '64vh',
        '& .mdxeditor': {
          minHeight: '64vh',
          color: 'text.primary',
          bgcolor: 'background.paper',
        },
        '& .mdxeditor-toolbar': {
          bgcolor: 'background.paper',
          borderBottom: '1px solid',
          borderColor: 'divider',
        },
        '& .mdxeditor-root-contenteditable': {
          minHeight: '56vh',
        },
        '& .cm-editor': {
          fontSize: 14,
        },
      }}
      onKeyDown={(event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
          event.preventDefault();
          onSaveShortcut?.();
        }
      }}
    >
      <MDXEditor
        key={initialValue}
        className={darkMode ? 'dark-theme' : undefined}
        readOnly={readOnly}
        markdown={value}
        onChange={onChange}
        plugins={[
          diffSourcePlugin({
            diffMarkdown: initialValue,
            viewMode: 'rich-text',
          }),
          toolbarPlugin({
            toolbarContents: () => (
              <DiffSourceToggleWrapper>
                <UndoRedo />
                <Separator />
                <BoldItalicUnderlineToggles />
                <CodeToggle />
                <Separator />
                <ListsToggle />
                <Separator />
                <BlockTypeSelect />
                <Separator />
                <CreateLink />
                <Separator />
                <InsertTable />
                <InsertThematicBreak />
                <Separator />
                <InsertCodeBlock />
              </DiffSourceToggleWrapper>
            ),
          }),
          listsPlugin(),
          quotePlugin(),
          headingsPlugin({ allowedHeadingLevels: [1, 2, 3] }),
          linkPlugin(),
          linkDialogPlugin(),
          tablePlugin(),
          thematicBreakPlugin(),
          codeBlockPlugin({ defaultCodeBlockLanguage: '' }),
          codeMirrorPlugin({
            codeBlockLanguages: {
              js: 'JavaScript',
              jsx: 'JSX',
              ts: 'TypeScript',
              tsx: 'TSX',
              css: 'CSS',
              html: 'HTML',
              json: 'JSON',
              md: 'Markdown',
              txt: 'Plain Text',
              sh: 'Shell',
              bash: 'Bash',
              yaml: 'YAML',
              java: 'Java',
              sql: 'SQL',
              python: 'Python',
              '': 'Plain Text',
            },
          }),
          markdownShortcutPlugin(),
        ]}
      />
    </Box>
  );
}
