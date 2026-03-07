﻿import { useState } from "react";

interface TopHeaderProps {
  wsConnected?: boolean;
  logoSrc?: string;
  lang: "en" | "zh";
  onToggleLang: () => void;
  searchValue: string;
  onSearchValueChange: (value: string) => void;
  onSearch: () => void;
  onClearSearch: () => void;
  isLoggedIn?: boolean;
  currentUser?: string | null;
  onLoginClick?: () => void;
  onRegisterClick?: () => void;
  onLogoutClick?: () => void;
  onUserClick?: () => void;
}

function LogoFallback() {
  return (
    <div className="h-11 px-3 rounded-lg border border-slate-200 bg-white text-slate-700 font-semibold flex items-center">
      STMS
    </div>
  );
}

export default function TopHeader({
  wsConnected = false,
  logoSrc = "/logo.png",
  lang,
  onToggleLang,
  searchValue,
  onSearchValueChange,
  onSearch,
  onClearSearch,
  isLoggedIn = false,
  currentUser = null,
  onLoginClick,
  onRegisterClick,
  onLogoutClick,
  onUserClick,
}: TopHeaderProps) {
  const [logoLoadError, setLogoLoadError] = useState(false);

  return (
    <header className="h-[70px] bg-white rounded-xl border border-slate-200 px-4 md:px-6 shadow-sm flex items-center justify-between gap-4">
      <div className="shrink-0">
        {logoLoadError ? (
          <LogoFallback />
        ) : (
          <img
            src={logoSrc}
            alt="STMS Logo"
            className="h-11 w-auto object-contain"
            onError={() => setLogoLoadError(true)}
            loading="eager"
            decoding="async"
          />
        )}
      </div>

      <div className="flex-1 flex justify-center">
        <div className="w-full max-w-[480px] flex items-center gap-2">
          <input
            type="text"
            placeholder={lang === "en" ? "Search by Security ID" : "按证券代码搜索"}
            className="w-full h-10 rounded-lg border border-slate-300 bg-slate-50 px-3 text-sm outline-none focus:ring-2 focus:ring-blue-200 focus:border-blue-400"
            value={searchValue}
            onChange={(e) => onSearchValueChange(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") onSearch();
            }}
          />
          <button
            type="button"
            className="h-10 px-4 rounded-lg bg-blue-600 text-white text-sm font-medium hover:bg-blue-700"
            onClick={onSearch}
          >
            {lang === "en" ? "Search" : "搜索"}
          </button>
          <button
            type="button"
            className="h-10 px-3 rounded-lg border border-slate-300 text-sm text-slate-700 hover:bg-slate-50"
            onClick={onClearSearch}
          >
            {lang === "en" ? "Clear" : "清空"}
          </button>
        </div>
      </div>

      <div className="shrink-0 flex items-center gap-2">
        <button
          type="button"
          onClick={onToggleLang}
          className="h-9 px-3 rounded-lg border border-slate-300 text-sm text-slate-700 hover:bg-slate-50"
        >
          EN / 中文
        </button>
        <span
          className={`hidden md:inline-flex items-center px-2.5 py-1 rounded-full text-xs ${
            wsConnected ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"
          }`}
        >
          {lang === "en"
            ? wsConnected
              ? "WS Online"
              : "WS Offline"
            : wsConnected
              ? "WS 已连接"
              : "WS 未连接"}
        </span>
        {!isLoggedIn ? (
          <>
            <button 
              type="button" 
              className="h-9 px-3 rounded-lg border border-slate-300 text-sm text-slate-700 hover:bg-slate-50"
              onClick={onLoginClick}
            >
              {lang === "en" ? "Login" : "登录"}
            </button>
            <button 
              type="button" 
              className="h-9 px-3 rounded-lg bg-slate-900 text-sm text-white hover:bg-black"
              onClick={onRegisterClick}
            >
              {lang === "en" ? "Register" : "注册"}
            </button>
          </>
        ) : (
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="h-9 px-2 rounded-lg border border-blue-200 bg-blue-50 text-sm text-blue-700 hover:bg-blue-100"
              onClick={onUserClick}
            >
              用户
            </button>
            <button
              type="button"
              className="h-9 px-3 rounded-lg border border-slate-300 text-sm text-gray-700 hover:bg-slate-50"
              onClick={onUserClick}
            >
              {currentUser}
            </button>
            <button 
              type="button" 
              className="h-9 px-3 rounded-lg border border-slate-300 text-sm text-slate-700 hover:bg-slate-50"
              onClick={onLogoutClick}
            >
              {lang === "en" ? "Logout" : "登出"}
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
