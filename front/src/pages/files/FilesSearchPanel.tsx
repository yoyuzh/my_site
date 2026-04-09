import React from 'react';
import { Input } from '@/src/components/ui/input';
import { Button } from '@/src/components/ui/button';

export function FilesSearchPanel({
  searchQuery,
  searchLoading,
  isSearchActive,
  searchError,
  onSearchQueryChange,
  onSearchSubmit,
  onClearSearch,
}: {
  searchQuery: string;
  searchLoading: boolean;
  isSearchActive: boolean;
  searchError: string;
  onSearchQueryChange: (query: string) => void;
  onSearchSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onClearSearch: () => void;
}) {
  return (
    <form className="border-b border-white/10 p-4 pt-0" onSubmit={onSearchSubmit}>
      <div className="mt-3 flex flex-col gap-2 md:flex-row">
        <Input
          value={searchQuery}
          onChange={(event) => onSearchQueryChange(event.target.value)}
          placeholder="按文件名搜索"
          className="h-10 border-white/10 bg-black/20 text-white placeholder:text-slate-500 focus-visible:ring-[#336EFF]"
        />
        <div className="flex gap-2">
          <Button type="submit" className="shrink-0" disabled={searchLoading}>
            {searchLoading ? '搜索中...' : '搜索'}
          </Button>
          {isSearchActive ? (
            <Button
              type="button"
              variant="outline"
              className="shrink-0 border-white/10 text-slate-300 hover:bg-white/10"
              onClick={onClearSearch}
            >
              清空
            </Button>
          ) : null}
        </div>
      </div>
      {searchError ? <p className="mt-2 text-sm text-red-400">{searchError}</p> : null}
    </form>
  );
}
