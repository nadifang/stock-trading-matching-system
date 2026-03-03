import { useState, useEffect } from "react";
import LoginForm from "./LoginForm";
import RegisterForm from "./RegisterForm";

interface AuthModalProps {
  isOpen: boolean;
  onClose: () => void;
  onLoginSuccess: () => void;
  onRegisterSuccess: () => void;
  lang: "en" | "zh";
  initialTab?: "login" | "register";
}

export default function AuthModal({
  isOpen,
  onClose,
  onLoginSuccess,
  onRegisterSuccess,
  lang,
  initialTab = 'login',
}: AuthModalProps) {
  const [activeTab, setActiveTab] = useState<"login" | "register">(initialTab);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 当initialTab变化时，更新activeTab并清除错误
  useEffect(() => {
    if (isOpen) {
      setActiveTab(initialTab);
      setError(null);
    }
  }, [initialTab, isOpen]);

  // 当切换标签页时，清除错误
  const handleTabChange = (tab: "login" | "register") => {
    setActiveTab(tab);
    setError(null);
  };

  // 模拟注册用户存储
  const registeredUsers = JSON.parse(localStorage.getItem('registeredUsers') || '[]');

  const handleLogin = async (shareholderId: string, password: string) => {
    setIsLoading(true);
    setError(null);
    try {
      // 模拟登录API调用
      await new Promise((resolve) => setTimeout(resolve, 1000));
      
      // 检查用户是否已注册
      const user = registeredUsers.find((u: any) => u.shareholderId === shareholderId && u.password === password);
      if (!user) {
        throw new Error('Invalid credentials');
      }
      
      // 登录成功，存储令牌
      localStorage.setItem('authToken', 'mock-token');
      localStorage.setItem('shareholderId', shareholderId);
      onLoginSuccess();
      onClose();
    } catch (err) {
      setError(lang === 'en' ? 'Login failed, please check your credentials' : '登录失败，请检查股东号和密码');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRegister = async (shareholderId: string, password: string, confirmPassword: string, name: string) => {
    setIsLoading(true);
    setError(null);
    try {
      if (password !== confirmPassword) {
        setError(lang === 'en' ? 'Passwords do not match' : '密码不匹配');
        return;
      }
      
      // 检查用户是否已存在
      const existingUser = registeredUsers.find((u: any) => u.shareholderId === shareholderId);
      if (existingUser) {
        setError(lang === 'en' ? 'User already exists' : '用户已存在');
        return;
      }
      
      // 模拟注册API调用
      await new Promise((resolve) => setTimeout(resolve, 1000));
      
      // 注册成功，存储用户信息
      const newUser = { shareholderId, password, name };
      const updatedUsers = [...registeredUsers, newUser];
      localStorage.setItem('registeredUsers', JSON.stringify(updatedUsers));
      
      // 存储令牌
      localStorage.setItem('authToken', 'mock-token');
      localStorage.setItem('shareholderId', shareholderId);
      onRegisterSuccess();
      onClose();
    } catch (err) {
      setError(lang === 'en' ? 'Registration failed, please try again' : '注册失败，请重试');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <div className="flex justify-between items-center mb-4">
          <h1 className="text-xl font-bold">{lang === 'en' ? 'User Authentication' : '用户认证'}</h1>
          <button
            type="button"
            className="text-gray-500 hover:text-gray-700 text-xl"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        <div className="border-b mb-4">
          <div className="flex">
            <button
              type="button"
              className={`px-4 py-2 ${activeTab === 'login' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500'}`}
              onClick={() => handleTabChange('login')}
            >
              {lang === 'en' ? 'Login' : '登录'}
            </button>
            <button
              type="button"
              className={`px-4 py-2 ${activeTab === 'register' ? 'border-b-2 border-blue-600 text-blue-600' : 'text-gray-500'}`}
              onClick={() => handleTabChange('register')}
            >
              {lang === 'en' ? 'Register' : '注册'}
            </button>
          </div>
        </div>
        {activeTab === 'login' ? (
          <LoginForm
            onLogin={handleLogin}
            onSwitchToRegister={() => setActiveTab('register')}
            isLoading={isLoading}
            error={error}
            lang={lang}
          />
        ) : (
          <RegisterForm
            onRegister={handleRegister}
            onSwitchToLogin={() => setActiveTab('login')}
            isLoading={isLoading}
            error={error}
            lang={lang}
          />
        )}
      </div>
    </div>
  );
}