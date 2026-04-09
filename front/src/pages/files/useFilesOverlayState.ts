import { useState } from 'react';
import type { UiFile } from './file-types';

export type NetdiskTargetAction = 'move' | 'copy';

export function useFilesOverlayState() {
  const [renameModalOpen, setRenameModalOpen] = useState(false);
  const [deleteModalOpen, setDeleteModalOpen] = useState(false);
  const [fileToRename, setFileToRename] = useState<UiFile | null>(null);
  const [fileToDelete, setFileToDelete] = useState<UiFile | null>(null);
  const [targetActionFile, setTargetActionFile] = useState<UiFile | null>(null);
  const [targetAction, setTargetAction] = useState<NetdiskTargetAction | null>(null);
  const [newFileName, setNewFileName] = useState('');
  const [activeDropdown, setActiveDropdown] = useState<number | null>(null);
  const [renameError, setRenameError] = useState('');
  const [isRenaming, setIsRenaming] = useState(false);

  const openRenameModal = (file: UiFile) => {
    setFileToRename(file);
    setNewFileName(file.name);
    setRenameError('');
    setRenameModalOpen(true);
  };

  const openDeleteModal = (file: UiFile) => {
    setFileToDelete(file);
    setDeleteModalOpen(true);
  };

  const openTargetActionModal = (file: UiFile, action: NetdiskTargetAction) => {
    setTargetAction(action);
    setTargetActionFile(file);
    setActiveDropdown(null);
  };

  return {
    renameModalOpen,
    setRenameModalOpen,
    deleteModalOpen,
    setDeleteModalOpen,
    fileToRename,
    setFileToRename,
    fileToDelete,
    setFileToDelete,
    targetActionFile,
    setTargetActionFile,
    targetAction,
    setTargetAction,
    newFileName,
    setNewFileName,
    activeDropdown,
    setActiveDropdown,
    renameError,
    setRenameError,
    isRenaming,
    setIsRenaming,
    openRenameModal,
    openDeleteModal,
    openTargetActionModal,
  };
}
