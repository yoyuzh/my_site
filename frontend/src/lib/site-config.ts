export interface SiteRuntimeConfig {
  siteName: string;
  siteDescription: string;
  registrationEnabled: boolean;
  passwordLoginEnabled: boolean;
  captchaEnabled: boolean;
  apiVersion: string;
}
import { apiRequest } from '../api/client';

export const defaultSiteRuntimeConfig: SiteRuntimeConfig = {
  siteName: 'Yoyuzh 网盘',
  siteDescription: '个人网盘与快速传输平台',
  registrationEnabled: true,
  passwordLoginEnabled: true,
  captchaEnabled: false,
  apiVersion: 'v2',
};

export async function loadSiteRuntimeConfig(signal?: AbortSignal): Promise<SiteRuntimeConfig> {
  return apiRequest<SiteRuntimeConfig>({
    url: '/v2/site/config',
    method: 'GET',
    authRequired: false,
    signal,
  });
}
