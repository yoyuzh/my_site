export function getOverviewLoadErrorMessage(isPostLoginFailure: boolean) {
  if (isPostLoginFailure) {
    return '登录已成功，但总览数据加载失败，请稍后重试。';
  }

  return '总览数据加载失败，请稍后重试。';
}
