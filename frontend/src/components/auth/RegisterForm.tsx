import { useState, useEffect } from "react";

interface RegisterFormProps {
  onRegister: (shareholderId: string, password: string, confirmPassword: string, name: string) => Promise<void>;
  onSwitchToLogin: () => void;
  isLoading: boolean;
  error: string | null;
  lang: "en" | "zh";
}

export default function RegisterForm({
  onRegister,
  onSwitchToLogin,
  isLoading,
  error,
  lang,
}: RegisterFormProps) {
  const [shareholderId, setShareholderId] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [localError, setLocalError] = useState<string | null>(null);

  // 当error prop变化时，更新本地错误状态
  useEffect(() => {
    setLocalError(null);
  }, [error]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (password !== confirmPassword) {
      setLocalError(lang === 'en' ? 'Passwords do not match' : '密码不匹配');
      return;
    }
    await onRegister(shareholderId, password, confirmPassword, name);
  };

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-center">{lang === 'en' ? 'Register' : '注册'}</h2>
      {(error || localError) && (
        <div className="bg-red-50 text-red-700 p-2 rounded">
          {error || localError}
        </div>
      )}
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === 'en' ? 'Shareholder ID' : '股东号'}
          </label>
          <input
            type="text"
            value={shareholderId}
            onChange={(e) => setShareholderId(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === 'en' ? 'Enter shareholder ID' : '请输入股东号'}
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === 'en' ? 'Name' : '姓名'}
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === 'en' ? 'Enter name' : '请输入姓名'}
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === 'en' ? 'Password' : '密码'}
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === 'en' ? 'Enter password' : '请输入密码'}
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === 'en' ? 'Confirm Password' : '确认密码'}
          </label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === 'en' ? 'Confirm password' : '请确认密码'}
            required
          />
        </div>
        <button
          type="submit"
          className="w-full bg-blue-600 text-white py-2 rounded-md hover:bg-blue-700"
          disabled={isLoading}
        >
          {isLoading ? (lang === 'en' ? 'Registering...' : '注册中...') : (lang === 'en' ? 'Register' : '注册')}
        </button>
      </form>
      <p className="text-center text-sm text-gray-600">
        {lang === 'en' ? 'Already have an account? ' : '已有账号？'}
        <button
          type="button"
          className="text-blue-600 hover:underline"
          onClick={onSwitchToLogin}
        >
          {lang === 'en' ? 'Login now' : '立即登录'}
        </button>
      </p>
    </div>
  );
}