import { useState } from "react";

interface LoginFormProps {
  onLogin: (shareholderId: string, password: string) => Promise<void>;
  onSwitchToRegister: () => void;
  isLoading: boolean;
  error: string | null;
  lang: "en" | "zh";
}

export default function LoginForm({
  onLogin,
  onSwitchToRegister,
  isLoading,
  error,
  lang,
}: LoginFormProps) {
  const [shareholderId, setShareholderId] = useState("");
  const [password, setPassword] = useState("");

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await onLogin(shareholderId, password);
  };

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-center">
        {lang === "en" ? "Login" : "Login"}
      </h2>
      {error && (
        <div className="bg-red-50 text-red-700 p-2 rounded">{error}</div>
      )}
      <form onSubmit={handleSubmit} className="space-y-3">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === "en" ? "Shareholder ID" : "Shareholder ID"}
          </label>
          <input
            type="text"
            value={shareholderId}
            onChange={(e) => setShareholderId(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={
              lang === "en" ? "Enter shareholder ID" : "Enter shareholder ID"
            }
            required
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === "en" ? "Password" : "Password"}
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === "en" ? "Enter password" : "Enter password"}
            required
          />
        </div>
        <button
          type="submit"
          className="w-full bg-blue-600 text-white py-2 rounded-md hover:bg-blue-700"
          disabled={isLoading}
        >
          {isLoading
            ? lang === "en"
              ? "Logging in..."
              : "Logging in..."
            : lang === "en"
              ? "Login"
              : "Login"}
        </button>
      </form>
      <p className="text-center text-sm text-gray-600">
        {lang === "en"
          ? "Don't have an account? "
          : "Don't have an account? "}
        <button
          type="button"
          className="text-blue-600 hover:underline"
          onClick={onSwitchToRegister}
        >
          {lang === "en" ? "Register now" : "Register now"}
        </button>
      </p>
    </div>
  );
}
