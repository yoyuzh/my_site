import { useRef, useState } from 'react';
import { searchFiles } from '@/src/lib/file-search';
import type { FileMetadata } from '@/src/lib/types';

export function useFilesSearchState() {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchAppliedQuery, setSearchAppliedQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FileMetadata[] | null>(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [selectedSearchFile, setSelectedSearchFile] = useState<FileMetadata | null>(null);
  const searchRequestIdRef = useRef(0);

  const clearSearchState = () => {
    searchRequestIdRef.current += 1;
    setSearchQuery('');
    setSearchAppliedQuery('');
    setSearchResults(null);
    setSearchLoading(false);
    setSearchError('');
    setSelectedSearchFile(null);
  };

  const executeSearch = async (query: string, onStart?: () => void) => {
    const nextQuery = query.trim();
    if (!nextQuery) {
      clearSearchState();
      return;
    }

    const requestId = searchRequestIdRef.current + 1;
    searchRequestIdRef.current = requestId;
    setSearchAppliedQuery(nextQuery);
    setSearchLoading(true);
    setSearchError('');
    setSearchResults(null);
    setSelectedSearchFile(null);
    onStart?.();

    try {
      const response = await searchFiles({
        name: nextQuery,
        type: 'all',
        page: 0,
        size: 100,
      });

      if (searchRequestIdRef.current !== requestId) return;
      setSearchResults(response.items);
    } catch (error) {
      if (searchRequestIdRef.current !== requestId) return;
      setSearchResults([]);
      setSearchError(error instanceof Error ? error.message : '搜索失败');
    } finally {
      if (searchRequestIdRef.current === requestId) {
        setSearchLoading(false);
      }
    }
  };

  const handleSearchSubmit = async (event: React.FormEvent<HTMLFormElement>, onStart?: () => void) => {
    event.preventDefault();
    await executeSearch(searchQuery, onStart);
  };

  const isSearchActive = searchAppliedQuery.trim().length > 0;

  return {
    searchQuery,
    setSearchQuery,
    searchAppliedQuery,
    searchResults,
    searchLoading,
    searchError,
    selectedSearchFile,
    setSelectedSearchFile,
    clearSearchState,
    handleSearchSubmit,
    isSearchActive,
  };
}
