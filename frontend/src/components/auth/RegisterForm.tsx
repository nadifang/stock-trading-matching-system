import { useState } from "react";

interface RegisterFormProps {
  onRegister: (
    shareholderId: string,
    password: string,
    confirmPassword: string,
    name: string
  ) => Promise<void>;
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
  const [shareholderId, setShareholderId] = useState("");
  const [name, setName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError(null);
    if (password !== confirmPassword) {
      setLocalError(
        lang === "en" ? "Passwords do not match" : "Passwords do not match"
      );
      return;
    }
    await onRegister(shareholderId, password, confirmPassword, name);
  };

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold text-center">
        {lang === "en" ? "Register" : "Register"}
      </h2>
      {(error || localError) && (
        <div className="bg-red-50 text-red-700 p-2 rounded">
          {error || localError}
        </div>
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
            {lang === "en" ? "Name" : "Name"}
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={lang === "en" ? "Enter name" : "Enter name"}
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
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {lang === "en" ? "Confirm Password" : "Confirm Password"}
          </label>
          <input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-md"
            placeholder={
              lang === "en" ? "Confirm password" : "Confirm password"
            }
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
              ? "Registering..."
              : "Registering..."
            : lang === "en"
              ? "Register"
              : "Register"}
        </button>
      </form>
      <p className="text-center text-sm text-gray-600">
        {lang === "en"
          ? "Already have an account? "
          : "Already have an account? "}
        <button
          type="button"
          className="text-blue-600 hover:underline"
          onClick={onSwitchToLogin}
        >
          {lang === "en" ? "Login now" : "Login now"}
        </button>
      </p>
    </div>
  );
}
