import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield,
  Server,
  Activity,
  Settings,
  Users,
  User,
  LogOut,
  Eye,
  EyeOff,
  Plus,
  Edit,
  Trash2,
  Power,
  PowerOff,
  RefreshCw,
  CheckCircle,
  XCircle,
  Search
} from 'lucide-react';

// ===========================================
// 1. 驗證服務 - API 呼叫模組
// ===========================================
const AuthService = {
  baseUrl: '/api/auth',

  async login(username: string, password: string) {
    const response = await fetch(`${this.baseUrl}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    return response.json();
  },

  async logout(token: string) {
    const response = await fetch(`${this.baseUrl}/logout`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    });
    return response.json();
  },

  async register(username: string, password: string, email: string) {
    const response = await fetch(`${this.baseUrl}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, email })
    });
    return response.json();
  },

  async validateToken(token: string) {
    const response = await fetch(`${this.baseUrl}/validate`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getCurrentUser(token: string) {
    const response = await fetch(`${this.baseUrl}/me`, {
      method: 'GET',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  }
};

// ===========================================
// 2. 代理服務 - API 呼叫模組
// ===========================================
const ProxyService = {
  baseUrl: '/api/proxy',

  async getAllProxies(token: string) {
    const response = await fetch(this.baseUrl, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getEnabledProxies(token: string) {
    const response = await fetch(`${this.baseUrl}/enabled`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async createProxy(token: string, proxyData: any) {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(proxyData)
    });
    return response.json();
  },

  async updateProxy(token: string, id: number, proxyData: any) {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(proxyData)
    });
    return response.json();
  },

  async updateProxyStatus(token: string, id: number, enable: boolean) {
    const response = await fetch(`${this.baseUrl}/${id}/status?enable=${enable}`, {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async deleteProxy(token: string, id: number) {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.ok;
  },

  async refreshProxies(token: string) {
    const response = await fetch(`${this.baseUrl}/refresh`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getActiveProxies(token: string) {
    const response = await fetch(`${this.baseUrl}/active`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getHealth(token: string) {
    const response = await fetch(`${this.baseUrl}/health`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  }
};

// ===========================================
// 3. 使用者個人設定服務 - API 呼叫模組
// ===========================================
const UserProfileService = {
  baseUrl: '/api/user/profile',

  async changePassword(token: string, currentPassword: string, newPassword: string) {
    const response = await fetch(`${this.baseUrl}/password`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ currentPassword, newPassword })
    });
    return response.json();
  }
};

// ===========================================
// 4. gRPC 呼叫記錄服務 - API 呼叫模組
// ===========================================
const GrpcCallLogService = {
  baseUrl: '/api/grpc-logs',

  async getAllLogs(token: string, page: number = 0, size: number = 20, sortBy: string = 'callStartTime', sortDirection: string = 'desc') {
    const response = await fetch(`${this.baseUrl}?page=${page}&size=${size}&sortBy=${sortBy}&sortDirection=${sortDirection}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async searchLogs(token: string, filters: any, page: number = 0, size: number = 20, sortBy: string = 'callStartTime', sortDirection: string = 'desc') {
    const params = new URLSearchParams({
      page: page.toString(),
      size: size.toString(),
      sortBy: sortBy,
      sortDirection: sortDirection
    });

    // 添加搜尋過濾器
    if (filters.clientIp) params.append('clientIp', filters.clientIp);
    if (filters.targetLocation) params.append('targetLocation', filters.targetLocation);
    if (filters.methodName) params.append('methodName', filters.methodName);
    if (filters.statusCode) params.append('statusCode', filters.statusCode);
    if (filters.callType) params.append('callType', filters.callType);
    if (filters.startTime) params.append('startTime', filters.startTime);
    if (filters.endTime) params.append('endTime', filters.endTime);

    const response = await fetch(`${this.baseUrl}/search?${params}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getLogsByClientIp(token: string, clientIp: string, page: number = 0, size: number = 20) {
    const response = await fetch(`${this.baseUrl}/by-client-ip?clientIp=${encodeURIComponent(clientIp)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getLogsByTargetLocation(token: string, targetLocation: string, page: number = 0, size: number = 20) {
    const response = await fetch(`${this.baseUrl}/by-target-location?targetLocation=${encodeURIComponent(targetLocation)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getLogsByMethodName(token: string, methodName: string, page: number = 0, size: number = 20) {
    const response = await fetch(`${this.baseUrl}/by-method-name?methodName=${encodeURIComponent(methodName)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getLogsByStatusCode(token: string, statusCode: string, page: number = 0, size: number = 20) {
    const response = await fetch(`${this.baseUrl}/by-status-code?statusCode=${encodeURIComponent(statusCode)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getLogsByTraceId(token: string, traceId: string, page: number = 0, size: number = 20) {
    const response = await fetch(`${this.baseUrl}/by-trace-id?traceId=${encodeURIComponent(traceId)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getRecentLogs(token: string) {
    const response = await fetch(`${this.baseUrl}/recent`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getSlowestLogs(token: string) {
    const response = await fetch(`${this.baseUrl}/slowest`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getStatistics(token: string) {
    const response = await fetch(`${this.baseUrl}/statistics`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async countCallsInTimeRange(token: string, startTime: string, endTime: string) {
    const response = await fetch(`${this.baseUrl}/count-by-time-range?startTime=${encodeURIComponent(startTime)}&endTime=${encodeURIComponent(endTime)}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async countCallsByStatusCode(token: string, statusCode: string) {
    const response = await fetch(`${this.baseUrl}/count-by-status-code?statusCode=${encodeURIComponent(statusCode)}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async cleanupOldLogs(token: string, cutoffTime: string) {
    const response = await fetch(`${this.baseUrl}/cleanup?cutoffTime=${encodeURIComponent(cutoffTime)}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.text();
  }
};

// ===========================================
// 5. 使用者管理服務 - API 呼叫模組
// ===========================================
const UserManagementService = {
  baseUrl: '/api/admin/users',

  async getAllUsers(token: string, page: number = 0, size: number = 10) {
    const response = await fetch(`${this.baseUrl}?page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async getUserById(token: string, id: number) {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async createUser(token: string, userData: any) {
    const response = await fetch(this.baseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(userData)
    });
    return response.json();
  },

  async updateUser(token: string, id: number, userData: any) {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(userData)
    });
    return response.json();
  },

  async deleteUser(token: string, id: number) {
    const response = await fetch(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async enableUser(token: string, id: number) {
    const response = await fetch(`${this.baseUrl}/${id}/enable`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async disableUser(token: string, id: number) {
    const response = await fetch(`${this.baseUrl}/${id}/disable`, {
      method: 'PUT',
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  },

  async updateUserRole(token: string, id: number, role: string) {
    const response = await fetch(`${this.baseUrl}/${id}/role`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ role })
    });
    return response.json();
  },

  async searchUsers(token: string, keyword: string, page: number = 0, size: number = 10) {
    const response = await fetch(`${this.baseUrl}/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    return response.json();
  }
};

// ===========================================
// 3. 驗證內容 - 狀態管理
// ===========================================
interface AuthContextType {
  user: any;
  token: string | null;
  login: (username: string, password: string) => Promise<{ success: boolean; error?: string }>;
  logout: () => Promise<void>;
  loading: boolean;
}

const AuthContext = React.createContext<AuthContextType | undefined>(undefined);

const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<any>(null);
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const validateToken = async () => {
      if (token) {
        try {
          const result = await AuthService.validateToken(token);
          if (result.valid) {
            const userInfo = await AuthService.getCurrentUser(token);
            setUser(userInfo);
          } else {
            localStorage.removeItem('token');
            setToken(null);
          }
        } catch (error) {
          localStorage.removeItem('token');
          setToken(null);
        }
      }
      setLoading(false);
    };

    validateToken();
  }, [token]);

  const login = async (username: string, password: string) => {
    try {
      const result = await AuthService.login(username, password);
      if (result.token) {
        setToken(result.token);
        localStorage.setItem('token', result.token);
        const userInfo = await AuthService.getCurrentUser(result.token);
        setUser(userInfo);
        return { success: true };
      }
      return { success: false, error: result.error || '登入失敗' };
    } catch (error: any) {
      return { success: false, error: error.message };
    }
  };

  const logout = async () => {
    if (token) {
      try {
        await AuthService.logout(token);
      } catch (error) {
        console.error('登出錯誤:', error);
      }
    }
    setUser(null);
    setToken(null);
    localStorage.removeItem('token');
  };

  return (
      <AuthContext.Provider value={{ user, token, login, logout, loading }}>
        {children}
      </AuthContext.Provider>
  );
};

const useAuth = (): AuthContextType => {
  const context = React.useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth 必須在 AuthProvider 內使用');
  }
  return context;
};

// ===========================================
// 4. 登入表單組件
// ===========================================
const LoginForm: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    const result = await login(username, password);
    if (!result.success) {
      setError(result.error || '登入失敗');
    }
    setLoading(false);
  };

  return (
      <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-blue-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full space-y-8">
          <div className="text-center">
            <div className="mx-auto h-16 w-16 bg-blue-600 rounded-full flex items-center justify-center mb-4">
              <Shield className="h-8 w-8 text-white" />
            </div>
            <h2 className="text-3xl font-bold text-gray-900">gStreamGate</h2>
            <p className="mt-2 text-gray-600">企業級 gRPC 代理管理平台</p>
          </div>

          <form onSubmit={handleSubmit} className="mt-8 space-y-6 bg-white p-8 rounded-xl shadow-lg">
            {error && (
                <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-lg">
                  {error}
                </div>
            )}

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                使用者名稱
              </label>
              <input
                  type="text"
                  value={username}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUsername(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  placeholder="請輸入使用者名稱"
                  required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                密碼
              </label>
              <div className="relative">
                <input
                    type={showPassword ? 'text' : 'password'}
                    value={password}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setPassword(e.target.value)}
                    className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="請輸入密碼"
                    required
                />
                <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <button
                type="submit"
                disabled={loading}
                className="w-full bg-blue-600 text-white py-2 px-4 rounded-lg hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? '登入中...' : '登入'}
            </button>

            <div className="text-center text-sm text-gray-500">
              <p>請使用您的帳戶登入</p>
            </div>
          </form>
        </div>
      </div>
  );
};

// ===========================================
// 5. 側邊導覽列組件
// ===========================================
interface SidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  const { user, logout } = useAuth();

  const isAdmin = user?.username === 'admin' || user?.role === 'ADMIN';

  const menuItems = [
    { id: 'dashboard', label: '儀表板', icon: Activity, adminOnly: false },
    { id: 'proxies', label: '代理管理', icon: Server, adminOnly: false },
    { id: 'grpc-logs', label: 'gRPC 呼叫記錄', icon: CheckCircle, adminOnly: false },
    { id: 'settings', label: '系統設定', icon: Settings, adminOnly: true },
    { id: 'users', label: '使用者管理', icon: Users, adminOnly: true }
  ];

  const visibleItems = menuItems.filter(item => !item.adminOnly || isAdmin);

  return (
      <div className="bg-gray-900 text-white w-64 min-h-screen flex flex-col">
        <div className="p-6 border-b border-gray-700">
          <div className="flex items-center space-x-3">
            <div className="h-10 w-10 bg-blue-600 rounded-lg flex items-center justify-center">
              <Shield className="h-6 w-6" />
            </div>
            <div>
              <h1 className="text-xl font-bold">gStreamGate</h1>
              <p className="text-gray-400 text-sm">v1.0.0</p>
            </div>
          </div>
        </div>

        <nav className="flex-1 px-4 py-6 space-y-2">
          {visibleItems.map((item) => {
            const Icon = item.icon;
            return (
                <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg transition-colors ${
                        activeTab === item.id
                            ? 'bg-blue-600 text-white'
                            : 'text-gray-300 hover:bg-gray-800 hover:text-white'
                    }`}
                >
                  <Icon className="h-5 w-5" />
                  <span>{item.label}</span>
                </button>
            );
          })}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <button
            onClick={() => setActiveTab('profile')}
            className={`w-full flex items-center space-x-3 mb-4 p-2 rounded-lg transition-colors ${
              activeTab === 'profile' 
                ? 'bg-blue-600 text-white' 
                : 'text-gray-300 hover:bg-gray-800 hover:text-white'
            }`}
          >
            <div className="h-8 w-8 bg-gray-600 rounded-full flex items-center justify-center">
              <User className="h-4 w-4" />
            </div>
            <div className="flex-1 text-left">
              <p className="text-sm font-medium">{user?.username}</p>
              <p className="text-xs text-gray-400">{isAdmin ? '管理員' : '使用者'}</p>
            </div>
          </button>
          <button
              onClick={logout}
              className="w-full flex items-center space-x-3 px-4 py-2 text-gray-300 hover:bg-gray-800 hover:text-white rounded-lg transition-colors"
          >
            <LogOut className="h-4 w-4" />
            <span>登出</span>
          </button>
        </div>
      </div>
  );
};

// ===========================================
// 6. 儀表板組件
// ===========================================
const Dashboard: React.FC = () => {
  const { token } = useAuth();
  const [stats, setStats] = useState({
    totalProxies: 0,
    enabledProxies: 0,
    activeConnections: 0,
    systemHealth: 'unknown'
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchStats = async () => {
      try {
        console.log('開始載入統計資料...');
        
        // 分別調用各個API，這樣可以看到具體哪個失敗了
        const allProxiesPromise = ProxyService.getAllProxies(token!).catch(err => {
          console.error('getAllProxies failed:', err);
          return [];
        });
        
        const healthPromise = ProxyService.getHealth(token!).catch(err => {
          console.error('getHealth failed:', err);
          return { status: 'DOWN' };
        });
        
        const activePromise = ProxyService.getActiveProxies(token!).catch(err => {
          console.error('getActiveProxies failed:', err);
          return { count: 0 };
        });

        const [allProxies, health, active] = await Promise.all([
          allProxiesPromise,
          healthPromise,
          activePromise
        ]);

        console.log('API 回應:', { allProxies, health, active });

        setStats({
          totalProxies: allProxies.length || 0,
          enabledProxies: allProxies.filter((p: any) => p.enable === 'Y').length || 0,
          activeConnections: active.count || 0,
          systemHealth: health.status === 'UP' ? 'healthy' : 'unhealthy'
        });
      } catch (error) {
        console.error('載入統計資料錯誤:', error);
        // 設置一個預設的異常狀態
        setStats(prev => ({
          ...prev,
          systemHealth: 'unhealthy'
        }));
      } finally {
        setLoading(false);
      }
    };

    fetchStats();
    const interval = setInterval(fetchStats, 30000);
    return () => clearInterval(interval);
  }, [token]);

  type ColorType = 'blue' | 'green' | 'purple' | 'red';

  const statCards: Array<{
    title: string;
    value: string | number;
    icon: any;
    color: ColorType;
  }> = [
    {
      title: '總代理數',
      value: stats.totalProxies,
      icon: Server,
      color: 'blue'
    },
    {
      title: '啟用代理',
      value: stats.enabledProxies,
      icon: CheckCircle,
      color: 'green'
    },
    {
      title: '活躍連線',
      value: stats.activeConnections,
      icon: Activity,
      color: 'purple'
    },
    {
      title: '系統狀態',
      value: stats.systemHealth === 'healthy' ? '正常' : '異常',
      icon: stats.systemHealth === 'healthy' ? CheckCircle : XCircle,
      color: stats.systemHealth === 'healthy' ? 'green' : 'red'
    }
  ];

  if (loading) {
    return (
        <div className="p-6">
          <div className="flex justify-center items-center h-64">
            <RefreshCw className="h-8 w-8 animate-spin text-blue-500" />
          </div>
        </div>
    );
  }

  return (
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <h1 className="text-2xl font-bold text-gray-900">儀表板</h1>
          <div className="text-sm text-gray-500">
            最後更新: {new Date().toLocaleString('zh-TW')}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {statCards.map((card, index) => {
            const Icon = card.icon;
            const colorClasses: Record<ColorType, string> = {
              blue: 'bg-blue-50 text-blue-600 border-blue-200',
              green: 'bg-green-50 text-green-600 border-green-200',
              purple: 'bg-purple-50 text-purple-600 border-purple-200',
              red: 'bg-red-50 text-red-600 border-red-200'
            };

            return (
                <div key={index} className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm text-gray-600 mb-1">{card.title}</p>
                      <p className="text-2xl font-bold text-gray-900">{card.value}</p>
                    </div>
                    <div className={`p-3 rounded-lg border ${colorClasses[card.color]}`}>
                      <Icon className="h-6 w-6" />
                    </div>
                  </div>
                </div>
            );
          })}
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">系統概覽</h2>
          <div className="space-y-4">
            <div className="flex justify-between items-center py-2 border-b border-gray-100">
              <span className="text-gray-600">平台版本</span>
              <span className="font-medium">gStreamGate v1.0.0</span>
            </div>
            <div className="flex justify-between items-center py-2 border-b border-gray-100">
              <span className="text-gray-600">Java 版本</span>
              <span className="font-medium">OpenJDK 21</span>
            </div>
            <div className="flex justify-between items-center py-2 border-b border-gray-100">
              <span className="text-gray-600">Spring Boot</span>
              <span className="font-medium">3.5.0</span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span className="text-gray-600">Web 伺服器</span>
              <span className="font-medium">Undertow</span>
            </div>
          </div>
        </div>
      </div>
  );
};

// ===========================================
// 7. 代理管理組件
// ===========================================
const ProxyManagement: React.FC = () => {
  const { token, user } = useAuth();
  const [proxies, setProxies] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingProxy, setEditingProxy] = useState<any>(null);

  const isAdmin = user?.username === 'admin' || user?.role === 'ADMIN';

  useEffect(() => {
    const fetchProxies = async () => {
      try {
        const data = await ProxyService.getAllProxies(token!);
        setProxies(data);
      } catch (error) {
        console.error('載入代理列表錯誤:', error);
      } finally {
        setLoading(false);
      }
    };

    if (token) {
      fetchProxies();
    }
  }, [token]);

  const refreshProxies = async () => {
    setLoading(true);
    try {
      const data = await ProxyService.getAllProxies(token!);
      setProxies(data);
    } catch (error) {
      console.error('載入代理列表錯誤:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusToggle = async (proxy: any) => {
    if (!isAdmin) return;

    try {
      const newStatus = proxy.enable === 'Y' ? false : true;
      await ProxyService.updateProxyStatus(token!, proxy.proxyMapId, newStatus);
      refreshProxies();
    } catch (error) {
      console.error('切換狀態錯誤:', error);
    }
  };

  const handleDelete = async (proxy: any) => {
    if (!isAdmin) return;

    if (window.confirm(`確定要刪除代理 "${proxy.proxyHostName}" 嗎？`)) {
      try {
        await ProxyService.deleteProxy(token!, proxy.proxyMapId);
        refreshProxies();
      } catch (error) {
        console.error('刪除代理錯誤:', error);
      }
    }
  };

  const filteredProxies = proxies.filter(proxy =>
      proxy.proxyHostName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      proxy.serviceName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      proxy.targetHostName?.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) {
    return (
        <div className="p-6">
          <div className="flex justify-center items-center h-64">
            <RefreshCw className="h-8 w-8 animate-spin text-blue-500" />
          </div>
        </div>
    );
  }

  return (
      <div className="p-6 space-y-6">
        <div className="flex justify-between items-center">
          <h1 className="text-2xl font-bold text-gray-900">代理管理</h1>
          <div className="flex space-x-3">
            <button
                onClick={refreshProxies}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors flex items-center space-x-2"
            >
              <RefreshCw className="h-4 w-4" />
              <span>重新整理</span>
            </button>
            {isAdmin && (
                <button
                    onClick={() => setShowCreateModal(true)}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
                >
                  <Plus className="h-4 w-4" />
                  <span>新增代理</span>
                </button>
            )}
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
          <div className="p-4 border-b border-gray-200">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
              <input
                  type="text"
                  placeholder="搜尋代理名稱、服務名稱或目標主機..."
                  value={searchTerm}
                  onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchTerm(e.target.value)}
                  className="pl-10 pr-4 py-2 w-full border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  服務資訊
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  目標位址
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  安全模式
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  狀態
                </th>
                {isAdmin && (
                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                      操作
                    </th>
                )}
              </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
              {filteredProxies.map((proxy) => (
                  <tr key={proxy.proxyMapId} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div>
                        <div className="text-sm font-medium text-gray-900">
                          {proxy.proxyHostName}
                        </div>
                        <div className="text-sm text-gray-500">
                          {proxy.serviceName}
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">
                        {proxy.targetHostName}:{proxy.targetPort}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                    <span className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                        proxy.secureMode === 'SECURE' ? 'bg-green-100 text-green-800' :
                            proxy.secureMode === 'PLAINTEXT' ? 'bg-gray-100 text-gray-800' :
                                'bg-blue-100 text-blue-800'
                    }`}>
                      {proxy.secureMode === 'SECURE' ? 'TLS' :
                          proxy.secureMode === 'PLAINTEXT' ? '明文' : '自動'}
                    </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center">
                        {proxy.enable === 'Y' ? (
                            <div className="flex items-center space-x-2">
                              <div className="h-2 w-2 bg-green-400 rounded-full"></div>
                              <span className="text-sm text-green-700">啟用</span>
                            </div>
                        ) : (
                            <div className="flex items-center space-x-2">
                              <div className="h-2 w-2 bg-gray-400 rounded-full"></div>
                              <span className="text-sm text-gray-500">停用</span>
                            </div>
                        )}
                      </div>
                    </td>
                    {isAdmin && (
                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                          <div className="flex space-x-2">
                            <button
                                onClick={() => handleStatusToggle(proxy)}
                                className={`p-1 rounded-lg transition-colors ${
                                    proxy.enable === 'Y'
                                        ? 'text-red-600 hover:bg-red-50'
                                        : 'text-green-600 hover:bg-green-50'
                                }`}
                                title={proxy.enable === 'Y' ? '停用' : '啟用'}
                            >
                              {proxy.enable === 'Y' ?
                                  <PowerOff className="h-4 w-4" /> :
                                  <Power className="h-4 w-4" />
                              }
                            </button>
                            <button
                                onClick={() => setEditingProxy(proxy)}
                                className="p-1 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                title="編輯"
                            >
                              <Edit className="h-4 w-4" />
                            </button>
                            <button
                                onClick={() => handleDelete(proxy)}
                                className="p-1 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                                title="刪除"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        </td>
                    )}
                  </tr>
              ))}
              </tbody>
            </table>

            {filteredProxies.length === 0 && (
                <div className="text-center py-12">
                  <Server className="mx-auto h-12 w-12 text-gray-400 mb-4" />
                  <h3 className="text-lg font-medium text-gray-900 mb-2">沒有找到代理</h3>
                  <p className="text-gray-500">
                    {searchTerm ? '請嘗試調整搜尋條件' : '還沒有配置任何代理服務'}
                  </p>
                </div>
            )}
          </div>
        </div>

        {/* 新增/編輯模態框 */}
        {(showCreateModal || editingProxy) && (
            <ProxyModal
                proxy={editingProxy}
                onClose={() => {
                  setShowCreateModal(false);
                  setEditingProxy(null);
                }}
                onSave={() => {
                  refreshProxies();
                  setShowCreateModal(false);
                  setEditingProxy(null);
                }}
                token={token!}
            />
        )}
      </div>
  );
};

// ===========================================
// 8. 代理設定模態框組件
// ===========================================
interface ProxyModalProps {
  proxy: any;
  onClose: () => void;
  onSave: () => void;
  token: string;
}

const ProxyModal: React.FC<ProxyModalProps> = ({ proxy, onClose, onSave, token }) => {
  const [formData, setFormData] = useState({
    serviceName: '',
    proxyHostName: '',
    targetHostName: '',
    targetPort: 8080,
    connectTimeoutMs: 5000,
    sendTimeoutMs: 10000,
    readTimeoutMs: 30000,
    secureMode: 'AUTO',
    enable: 'Y',
    autoTrustUpstreamCerts: 'N',
    trustedCertsContent: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const isEditing = !!proxy;

  useEffect(() => {
    if (proxy) {
      setFormData({
        serviceName: proxy.serviceName || '',
        proxyHostName: proxy.proxyHostName || '',
        targetHostName: proxy.targetHostName || '',
        targetPort: proxy.targetPort || 8080,
        connectTimeoutMs: proxy.connectTimeoutMs || 5000,
        sendTimeoutMs: proxy.sendTimeoutMs || 10000,
        readTimeoutMs: proxy.readTimeoutMs || 30000,
        secureMode: proxy.secureMode || 'AUTO',
        enable: proxy.enable || 'Y',
        autoTrustUpstreamCerts: proxy.autoTrustUpstreamCerts || 'N',
        trustedCertsContent: proxy.trustedCertsContent || ''
      });
    }
  }, [proxy]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      if (isEditing) {
        await ProxyService.updateProxy(token, proxy.proxyMapId, formData);
      } else {
        await ProxyService.createProxy(token, formData);
      }
      onSave();
    } catch (error: any) {
      setError(error.message || '儲存失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
        <div className="bg-white rounded-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
          <div className="p-6 border-b border-gray-200">
            <h2 className="text-xl font-bold text-gray-900">
              {isEditing ? '編輯代理設定' : '新增代理設定'}
            </h2>
          </div>

          <form onSubmit={handleSubmit} className="p-6 space-y-6">
            {error && (
                <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-lg">
                  {error}
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  服務名稱 *
                </label>
                <input
                    type="text"
                    value={formData.serviceName}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, serviceName: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  代理主機名稱 *
                </label>
                <input
                    type="text"
                    value={formData.proxyHostName}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, proxyHostName: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="api.example.com"
                    required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  目標主機名稱 *
                </label>
                <input
                    type="text"
                    value={formData.targetHostName}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, targetHostName: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="backend.internal"
                    required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  目標埠號 *
                </label>
                <input
                    type="number"
                    value={formData.targetPort}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, targetPort: parseInt(e.target.value) || 8080})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    min="1"
                    max="65535"
                    required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  安全模式
                </label>
                <select
                    value={formData.secureMode}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setFormData({...formData, secureMode: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="AUTO">自動偵測</option>
                  <option value="SECURE">強制 TLS</option>
                  <option value="PLAINTEXT">明文傳輸</option>
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  狀態
                </label>
                <select
                    value={formData.enable}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => setFormData({...formData, enable: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                >
                  <option value="Y">啟用</option>
                  <option value="N">停用</option>
                </select>
              </div>
            </div>

            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4">逾時設定</h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    連線逾時 (ms)
                  </label>
                  <input
                      type="number"
                      value={formData.connectTimeoutMs}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, connectTimeoutMs: parseInt(e.target.value) || 5000})}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      min="1000"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    傳送逾時 (ms)
                  </label>
                  <input
                      type="number"
                      value={formData.sendTimeoutMs}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, sendTimeoutMs: parseInt(e.target.value) || 10000})}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      min="1000"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    讀取逾時 (ms)
                  </label>
                  <input
                      type="number"
                      value={formData.readTimeoutMs}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({...formData, readTimeoutMs: parseInt(e.target.value) || 30000})}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      min="1000"
                  />
                </div>
              </div>
            </div>

            <div>
              <h3 className="text-lg font-medium text-gray-900 mb-4">TLS 設定</h3>
              <div className="space-y-4">
                <div>
                  <label className="flex items-center space-x-3">
                    <input
                        type="checkbox"
                        checked={formData.autoTrustUpstreamCerts === 'Y'}
                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFormData({
                          ...formData,
                          autoTrustUpstreamCerts: e.target.checked ? 'Y' : 'N'
                        })}
                        className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                    />
                    <span className="text-sm text-gray-700">自動信任上游憑證</span>
                  </label>
                  <p className="text-xs text-gray-500 mt-1">
                    ⚠️ 僅用於測試環境，生產環境請提供受信任的憑證
                  </p>
                </div>

                {formData.autoTrustUpstreamCerts === 'N' && (
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">
                        受信任的 CA 憑證 (PEM 格式)
                      </label>
                      <textarea
                          value={formData.trustedCertsContent}
                          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setFormData({...formData, trustedCertsContent: e.target.value})}
                          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                          rows={6}
                          placeholder="-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----"
                      />
                    </div>
                )}
              </div>
            </div>

            <div className="flex justify-end space-x-3 pt-6 border-t border-gray-200">
              <button
                  type="button"
                  onClick={onClose}
                  className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                取消
              </button>
              <button
                  type="submit"
                  disabled={loading}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {loading ? '儲存中...' : (isEditing ? '更新' : '建立')}
              </button>
            </div>
          </form>
        </div>
      </div>
  );
};

// ===========================================
// 9. 系統設定組件
// ===========================================
const SystemSettings: React.FC = () => {
  const { token } = useAuth();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const handleRefreshAll = async () => {
    setLoading(true);
    setMessage('');
    try {
      await ProxyService.refreshProxies(token!);
      setMessage('所有代理設定已重新載入');
    } catch (error: any) {
      setMessage('重新載入失敗: ' + error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
      <div className="p-6 space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">系統設定</h1>

        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">代理服務控制</h2>

          {message && (
              <div className={`mb-4 px-4 py-3 rounded-lg ${
                  message.includes('失敗')
                      ? 'bg-red-50 border border-red-200 text-red-600'
                      : 'bg-green-50 border border-green-200 text-green-600'
              }`}>
                {message}
              </div>
          )}

          <div className="space-y-4">
            <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
              <div>
                <h3 className="font-medium text-gray-900">重新載入所有代理</h3>
                <p className="text-sm text-gray-600">從資料庫重新載入所有代理設定並重新啟動服務</p>
              </div>
              <button
                  onClick={handleRefreshAll}
                  disabled={loading}
                  className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center space-x-2"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                <span>{loading ? '執行中...' : '執行'}</span>
              </button>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">系統資訊</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <h3 className="font-medium text-gray-900 mb-3">應用程式資訊</h3>
              <dl className="space-y-2">
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">版本</dt>
                  <dd className="text-sm font-medium">v1.0.0</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">建置日期</dt>
                  <dd className="text-sm font-medium">2025-06-05</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">Java 版本</dt>
                  <dd className="text-sm font-medium">OpenJDK 21</dd>
                </div>
              </dl>
            </div>
            <div>
              <h3 className="font-medium text-gray-900 mb-3">框架資訊</h3>
              <dl className="space-y-2">
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">Spring Boot</dt>
                  <dd className="text-sm font-medium">3.5.0</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">Web 伺服器</dt>
                  <dd className="text-sm font-medium">Undertow</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-sm text-gray-600">gRPC</dt>
                  <dd className="text-sm font-medium">1.68.1</dd>
                </div>
              </dl>
            </div>
          </div>
        </div>
      </div>
  );
};

// ===========================================
// 10. 使用者管理組件
// ===========================================
const UserManagement: React.FC = () => {
  const { token, user } = useAuth();
  const [users, setUsers] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState<any>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const pageSize = 10;

  const isAdmin = user?.username === 'admin' || user?.role === 'ADMIN';

  useEffect(() => {
    if (token && isAdmin) {
      fetchUsers();
    }
  }, [token, isAdmin, currentPage]); // eslint-disable-line react-hooks/exhaustive-deps

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = searchTerm 
        ? await UserManagementService.searchUsers(token!, searchTerm, currentPage, pageSize)
        : await UserManagementService.getAllUsers(token!, currentPage, pageSize);
      
      setUsers(response.users || []);
      setTotalPages(response.totalPages || 0);
      setTotalElements(response.totalElements || 0);
    } catch (error) {
      console.error('載入使用者列表錯誤:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    setCurrentPage(0);
    fetchUsers();
  };

  const handleStatusToggle = async (userId: number, currentEnabled: boolean) => {
    try {
      if (currentEnabled) {
        await UserManagementService.disableUser(token!, userId);
      } else {
        await UserManagementService.enableUser(token!, userId);
      }
      fetchUsers();
    } catch (error: any) {
      alert(error.message || '切換狀態失敗');
    }
  };

  const handleRoleChange = async (userId: number, newRole: string) => {
    try {
      await UserManagementService.updateUserRole(token!, userId, newRole);
      fetchUsers();
    } catch (error: any) {
      alert(error.message || '更新角色失敗');
    }
  };

  const handleDelete = async (userId: number, username: string) => {
    if (window.confirm(`確定要刪除使用者 "${username}" 嗎？`)) {
      try {
        await UserManagementService.deleteUser(token!, userId);
        fetchUsers();
      } catch (error: any) {
        alert(error.message || '刪除使用者失敗');
      }
    }
  };

  if (!isAdmin) {
    return (
      <div className="p-6 space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">使用者管理</h1>
        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
          <div className="text-center py-12">
            <Users className="mx-auto h-12 w-12 text-gray-400 mb-4" />
            <h3 className="text-lg font-medium text-gray-900 mb-2">權限不足</h3>
            <p className="text-gray-500">
              您需要管理員權限才能訪問使用者管理功能
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="p-6">
        <div className="flex justify-center items-center h-64">
          <RefreshCw className="h-8 w-8 animate-spin text-blue-500" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">使用者管理</h1>
        <div className="flex space-x-3">
          <button
            onClick={fetchUsers}
            className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors flex items-center space-x-2"
          >
            <RefreshCw className="h-4 w-4" />
            <span>重新整理</span>
          </button>
          <button
            onClick={() => setShowCreateModal(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center space-x-2"
          >
            <Plus className="h-4 w-4" />
            <span>新增使用者</span>
          </button>
        </div>
      </div>

      {/* 搜尋區域 */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm">
        <div className="p-4 border-b border-gray-200">
          <div className="flex space-x-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
              <input
                type="text"
                placeholder="搜尋使用者名稱或電子郵件..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
                className="pl-10 pr-4 py-2 w-full border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
            <button
              onClick={handleSearch}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              搜尋
            </button>
            {searchTerm && (
              <button
                onClick={() => {
                  setSearchTerm('');
                  setCurrentPage(0);
                  fetchUsers();
                }}
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
              >
                清除
              </button>
            )}
          </div>
        </div>

        {/* 使用者列表 */}
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  使用者資訊
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  角色
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  狀態
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  建立時間
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  最後登入
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {users.map((userData) => (
                <tr key={userData.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div>
                      <div className="text-sm font-medium text-gray-900">
                        {userData.username}
                      </div>
                      <div className="text-sm text-gray-500">
                        {userData.email}
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <select
                      value={userData.role}
                      onChange={(e) => handleRoleChange(userData.id, e.target.value)}
                      className="text-sm border border-gray-300 rounded px-2 py-1 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    >
                      <option value="USER">使用者</option>
                      <option value="ADMIN">管理員</option>
                    </select>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap">
                    <div className="flex items-center">
                      {userData.enabled ? (
                        <div className="flex items-center space-x-2">
                          <div className="h-2 w-2 bg-green-400 rounded-full"></div>
                          <span className="text-sm text-green-700">啟用</span>
                        </div>
                      ) : (
                        <div className="flex items-center space-x-2">
                          <div className="h-2 w-2 bg-red-400 rounded-full"></div>
                          <span className="text-sm text-red-700">停用</span>
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {userData.createdDate ? new Date(userData.createdDate).toLocaleDateString('zh-TW') : '-'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {userData.lastLogin ? new Date(userData.lastLogin).toLocaleDateString('zh-TW') : '未登入'}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div className="flex space-x-2">
                      <button
                        onClick={() => handleStatusToggle(userData.id, userData.enabled)}
                        className={`p-1 rounded-lg transition-colors ${
                          userData.enabled
                            ? 'text-red-600 hover:bg-red-50'
                            : 'text-green-600 hover:bg-green-50'
                        }`}
                        title={userData.enabled ? '停用' : '啟用'}
                      >
                        {userData.enabled ? 
                          <PowerOff className="h-4 w-4" /> : 
                          <Power className="h-4 w-4" />
                        }
                      </button>
                      <button
                        onClick={() => setEditingUser(userData)}
                        className="p-1 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                        title="編輯"
                      >
                        <Edit className="h-4 w-4" />
                      </button>
                      <button
                        onClick={() => handleDelete(userData.id, userData.username)}
                        className="p-1 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                        title="刪除"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {users.length === 0 && (
            <div className="text-center py-12">
              <Users className="mx-auto h-12 w-12 text-gray-400 mb-4" />
              <h3 className="text-lg font-medium text-gray-900 mb-2">沒有找到使用者</h3>
              <p className="text-gray-500">
                {searchTerm ? '請嘗試調整搜尋條件' : '還沒有使用者資料'}
              </p>
            </div>
          )}
        </div>

        {/* 分頁 */}
        {totalPages > 1 && (
          <div className="px-6 py-3 border-t border-gray-200 flex items-center justify-between">
            <div className="text-sm text-gray-500">
              顯示 {currentPage * pageSize + 1} 到 {Math.min((currentPage + 1) * pageSize, totalElements)} 筆，共 {totalElements} 筆
            </div>
            <div className="flex space-x-2">
              <button
                onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
                disabled={currentPage === 0}
                className="px-3 py-1 border border-gray-300 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                上一頁
              </button>
              <span className="px-3 py-1 text-sm">
                第 {currentPage + 1} 頁，共 {totalPages} 頁
              </span>
              <button
                onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
                disabled={currentPage >= totalPages - 1}
                className="px-3 py-1 border border-gray-300 rounded text-sm disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-50"
              >
                下一頁
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 新增/編輯模態框 */}
      {(showCreateModal || editingUser) && (
        <UserModal
          user={editingUser}
          onClose={() => {
            setShowCreateModal(false);
            setEditingUser(null);
          }}
          onSave={() => {
            fetchUsers();
            setShowCreateModal(false);
            setEditingUser(null);
          }}
          token={token!}
        />
      )}
    </div>
  );
};

// ===========================================
// 11. 使用者模態框組件
// ===========================================
interface UserModalProps {
  user: any;
  onClose: () => void;
  onSave: () => void;
  token: string;
}

const UserModal: React.FC<UserModalProps> = ({ user, onClose, onSave, token }) => {
  const [formData, setFormData] = useState({
    username: '',
    password: '',
    email: '',
    role: 'USER',
    enabled: true
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [changePassword, setChangePassword] = useState(false);
  const [copyMessage, setCopyMessage] = useState('');

  const isEditing = !!user;
  
  // 調試資訊
  console.log('UserModal - user:', user);
  console.log('UserModal - isEditing:', isEditing);

  // 產生安全密碼函數 (12位數，英文大小寫與數字)
  const generateSecurePassword = () => {
    const uppercase = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    const lowercase = 'abcdefghijklmnopqrstuvwxyz';
    const numbers = '0123456789';
    const allChars = uppercase + lowercase + numbers;
    
    let password = '';
    
    // 確保至少包含每種字符類型
    password += uppercase[Math.floor(Math.random() * uppercase.length)];
    password += lowercase[Math.floor(Math.random() * lowercase.length)];
    password += numbers[Math.floor(Math.random() * numbers.length)];
    
    // 填充到12位數
    for (let i = 3; i < 12; i++) {
      password += allChars[Math.floor(Math.random() * allChars.length)];
    }
    
    // 打亂順序
    return password.split('').sort(() => Math.random() - 0.5).join('');
  };

  // 複製到剪貼簿並顯示提示
  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopyMessage('密碼已複製到剪貼簿！');
      setTimeout(() => setCopyMessage(''), 3000);
    } catch (err) {
      // 如果 clipboard API 不可用，使用傳統方法
      const textArea = document.createElement('textarea');
      textArea.value = text;
      document.body.appendChild(textArea);
      textArea.select();
      document.execCommand('copy');
      document.body.removeChild(textArea);
      setCopyMessage('密碼已複製到剪貼簿！');
      setTimeout(() => setCopyMessage(''), 3000);
    }
  };

  // 一鍵產生並複製密碼
  const handleGeneratePassword = () => {
    const newPassword = generateSecurePassword();
    setFormData({...formData, password: newPassword});
    copyToClipboard(newPassword);
  };

  useEffect(() => {
    if (user) {
      setFormData({
        username: user.username || '',
        password: '', // 編輯時不顯示密碼
        email: user.email || '',
        role: user.role || 'USER',
        enabled: user.enabled !== false
      });
    }
  }, [user]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      if (isEditing) {
        const updateData: any = {
          username: formData.username,
          email: formData.email,
          role: formData.role,
          enabled: formData.enabled
        };
        // 加入密碼更新邏輯
        if (changePassword && formData.password) {
          updateData.password = formData.password;
        }
        await UserManagementService.updateUser(token, user.id, updateData);
      } else {
        await UserManagementService.createUser(token, formData);
      }
      onSave();
    } catch (error: any) {
      setError(error.message || '儲存失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-xl max-w-md w-full">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-xl font-bold text-gray-900">
            {isEditing ? '編輯使用者' : '新增使用者'}
          </h2>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-600 px-4 py-3 rounded-lg">
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              使用者名稱 *
            </label>
            <input
              type="text"
              value={formData.username}
              onChange={(e) => setFormData({...formData, username: e.target.value})}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              required
            />
          </div>

          {/* 密碼區塊 */}
          {!isEditing ? (
            // 新增使用者時的密碼欄位
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                密碼 *
              </label>
              <div className="space-y-3">
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={formData.password}
                    onChange={(e) => setFormData({...formData, password: e.target.value})}
                    className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    placeholder="請輸入密碼"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                  >
                    {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
                <div className="flex space-x-2">
                  <button
                    type="button"
                    onClick={handleGeneratePassword}
                    className="flex-1 px-3 py-2 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 transition-colors flex items-center justify-center space-x-2"
                  >
                    <RefreshCw className="h-4 w-4" />
                    <span>一鍵產生安全密碼</span>
                  </button>
                </div>
                {copyMessage && (
                  <div className="text-sm text-green-600 bg-green-50 px-3 py-2 rounded-lg">
                    {copyMessage}
                  </div>
                )}
              </div>
            </div>
          ) : (
            // 編輯使用者時的密碼變更區塊
            <div>
              <div className="flex items-center justify-between mb-3">
                <label className="block text-sm font-medium text-gray-700">
                  密碼
                </label>
                <button
                  type="button"
                  onClick={() => setChangePassword(!changePassword)}
                  className="text-sm text-blue-600 hover:text-blue-800"
                >
                  {changePassword ? '取消修改密碼' : '修改密碼'}
                </button>
              </div>
              
              {changePassword && (
                <div className="space-y-3">
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={formData.password}
                      onChange={(e) => setFormData({...formData, password: e.target.value})}
                      className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      placeholder="輸入新密碼"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
                    >
                      {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                    </button>
                  </div>
                  <div className="flex space-x-2">
                    <button
                      type="button"
                      onClick={handleGeneratePassword}
                      className="flex-1 px-3 py-2 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 transition-colors flex items-center justify-center space-x-2"
                    >
                      <RefreshCw className="h-4 w-4" />
                      <span>一鍵產生安全密碼</span>
                    </button>
                  </div>
                  {copyMessage && (
                    <div className="text-sm text-green-600 bg-green-50 px-3 py-2 rounded-lg">
                      {copyMessage}
                    </div>
                  )}
                  <div className="text-xs text-gray-500">
                    密碼將包含大小寫字母和數字，長度12位數
                  </div>
                </div>
              )}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              電子郵件 *
            </label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({...formData, email: e.target.value})}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              角色
            </label>
            <select
              value={formData.role}
              onChange={(e) => setFormData({...formData, role: e.target.value})}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value="USER">使用者</option>
              <option value="ADMIN">管理員</option>
            </select>
          </div>

          <div>
            <label className="flex items-center space-x-3">
              <input
                type="checkbox"
                checked={formData.enabled}
                onChange={(e) => setFormData({...formData, enabled: e.target.checked})}
                className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
              />
              <span className="text-sm text-gray-700">啟用帳戶</span>
            </label>
          </div>

          <div className="flex justify-end space-x-3 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              取消
            </button>
            <button
              type="submit"
              disabled={loading}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? '儲存中...' : (isEditing ? '更新' : '建立')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ===========================================
// 11. 個人設定組件
// ===========================================
const UserProfile: React.FC = () => {
  const { token, user } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const handlePasswordChange = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setMessage('');
    setError('');

    if (newPassword !== confirmPassword) {
      setError('新密碼與確認密碼不符');
      setLoading(false);
      return;
    }

    if (newPassword.length < 6) {
      setError('新密碼長度至少6個字符');
      setLoading(false);
      return;
    }

    try {
      const result = await UserProfileService.changePassword(token!, currentPassword, newPassword);
      if (result.error) {
        setError(result.error);
      } else {
        setMessage('密碼變更成功');
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
      }
    } catch (error: any) {
      setError(error.message || '密碼變更失敗');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-6 space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">個人設定</h1>

      {/* 用戶資訊 */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">帳戶資訊</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              使用者名稱
            </label>
            <input
              type="text"
              value={user?.username || ''}
              disabled
              className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              角色
            </label>
            <input
              type="text"
              value={user?.role === 'ADMIN' ? '管理員' : '使用者'}
              disabled
              className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-500"
            />
          </div>
        </div>
      </div>

      {/* 密碼變更 */}
      <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">變更密碼</h2>
        
        {message && (
          <div className="mb-4 px-4 py-3 rounded-lg bg-green-50 border border-green-200 text-green-600">
            {message}
          </div>
        )}
        
        {error && (
          <div className="mb-4 px-4 py-3 rounded-lg bg-red-50 border border-red-200 text-red-600">
            {error}
          </div>
        )}

        <form onSubmit={handlePasswordChange} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              目前密碼 *
            </label>
            <div className="relative">
              <input
                type={showCurrentPassword ? 'text' : 'password'}
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="請輸入目前密碼"
                required
              />
              <button
                type="button"
                onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
              >
                {showCurrentPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              新密碼 *
            </label>
            <div className="relative">
              <input
                type={showNewPassword ? 'text' : 'password'}
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="請輸入新密碼（至少6個字符）"
                required
              />
              <button
                type="button"
                onClick={() => setShowNewPassword(!showNewPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
              >
                {showNewPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              確認新密碼 *
            </label>
            <div className="relative">
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                className="w-full px-3 py-2 pr-10 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="請再次輸入新密碼"
                required
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600"
              >
                {showConfirmPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={loading}
              className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {loading ? '變更中...' : '變更密碼'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ===========================================
// 12. gRPC 呼叫記錄組件
// ===========================================
const GrpcCallLogs: React.FC = () => {
  const { token } = useAuth();
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sortBy, setSortBy] = useState('callStartTime');
  const [sortDirection, setSortDirection] = useState('desc');
  
  // 搜尋過濾器
  const [filters, setFilters] = useState({
    clientIp: '',
    targetLocation: '',
    methodName: '',
    statusCode: '',
    callType: '',
    startTime: '',
    endTime: ''
  });
  
  const [showFilters, setShowFilters] = useState(false);
  const [statistics, setStatistics] = useState<any>(null);

  const fetchLogs = useCallback(async () => {
    try {
      setLoading(true);
      let response;
      
      // 檢查是否有任何過濾器
      const hasFilters = Object.values(filters).some(value => value !== '');
      
      if (hasFilters) {
        response = await GrpcCallLogService.searchLogs(token!, filters, currentPage, pageSize, sortBy, sortDirection);
      } else {
        response = await GrpcCallLogService.getAllLogs(token!, currentPage, pageSize, sortBy, sortDirection);
      }
      
      setLogs(response.content || []);
      setTotalPages(response.totalPages || 0);
    } catch (error) {
      console.error('載入 gRPC 呼叫記錄錯誤:', error);
    } finally {
      setLoading(false);
    }
  }, [token, filters, currentPage, pageSize, sortBy, sortDirection]);

  const fetchStatistics = useCallback(async () => {
    try {
      const stats = await GrpcCallLogService.getStatistics(token!);
      setStatistics(stats);
    } catch (error) {
      console.error('載入統計資料錯誤:', error);
    }
  }, [token]);

  useEffect(() => {
    if (token) {
      fetchLogs();
      fetchStatistics();
    }
  }, [token, currentPage, pageSize, sortBy, sortDirection, fetchLogs, fetchStatistics]);

  const handleSearch = () => {
    setCurrentPage(0);
    fetchLogs();
  };

  const handleClearFilters = () => {
    setFilters({
      clientIp: '',
      targetLocation: '',
      methodName: '',
      statusCode: '',
      callType: '',
      startTime: '',
      endTime: ''
    });
    setCurrentPage(0);
    setTimeout(() => fetchLogs(), 100);
  };

  const handleSort = (column: string) => {
    if (sortBy === column) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortBy(column);
      setSortDirection('desc');
    }
    setCurrentPage(0);
  };

  const formatDateTime = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-TW');
  };

  const formatDuration = (ms: number) => {
    if (ms < 1000) return `${ms}ms`;
    if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
    return `${(ms / 60000).toFixed(1)}m`;
  };

  const getStatusColor = (statusCode: string) => {
    if (statusCode === 'OK') return 'text-green-600 bg-green-50';
    if (statusCode.includes('ERROR') || statusCode.includes('FAILED')) return 'text-red-600 bg-red-50';
    if (statusCode === 'CANCELLED') return 'text-yellow-600 bg-yellow-50';
    return 'text-gray-600 bg-gray-50';
  };

  if (loading && logs.length === 0) {
    return (
      <div className="p-6">
        <div className="flex justify-center items-center h-64">
          <RefreshCw className="h-8 w-8 animate-spin text-blue-500" />
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex justify-between items-center">
        <h1 className="text-2xl font-bold text-gray-900">gRPC 呼叫記錄</h1>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => setShowFilters(!showFilters)}
            className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Search className="h-4 w-4" />
            <span>搜尋過濾</span>
          </button>
          <button
            onClick={fetchLogs}
            disabled={loading}
            className="flex items-center space-x-2 px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            <span>重新載入</span>
          </button>
        </div>
      </div>

      {/* 統計資料 */}
      {statistics && (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-white p-4 rounded-lg border">
            <div className="text-sm text-gray-600">總呼叫數</div>
            <div className="text-2xl font-bold text-blue-600">{statistics.totalCalls || 0}</div>
          </div>
          <div className="bg-white p-4 rounded-lg border">
            <div className="text-sm text-gray-600">成功呼叫</div>
            <div className="text-2xl font-bold text-green-600">{statistics.successfulCalls || 0}</div>
          </div>
          <div className="bg-white p-4 rounded-lg border">
            <div className="text-sm text-gray-600">失敗呼叫</div>
            <div className="text-2xl font-bold text-red-600">{statistics.failedCalls || 0}</div>
          </div>
          <div className="bg-white p-4 rounded-lg border">
            <div className="text-sm text-gray-600">平均執行時間</div>
            <div className="text-2xl font-bold text-purple-600">{formatDuration(statistics.avgExecutionTime || 0)}</div>
          </div>
        </div>
      )}

      {/* 搜尋過濾器 */}
      {showFilters && (
        <div className="bg-white p-6 rounded-lg border border-gray-200">
          <h3 className="text-lg font-semibold mb-4">搜尋過濾器</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">客戶端 IP</label>
              <input
                type="text"
                value={filters.clientIp}
                onChange={(e) => setFilters({...filters, clientIp: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="例如: 192.168.1.1"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">目標位置</label>
              <input
                type="text"
                value={filters.targetLocation}
                onChange={(e) => setFilters({...filters, targetLocation: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="例如: example.com:8080"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">呼叫方法</label>
              <input
                type="text"
                value={filters.methodName}
                onChange={(e) => setFilters({...filters, methodName: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                placeholder="例如: /service/Method"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">狀態碼</label>
              <select
                value={filters.statusCode}
                onChange={(e) => setFilters({...filters, statusCode: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="">全部</option>
                <option value="OK">OK</option>
                <option value="CANCELLED">CANCELLED</option>
                <option value="UNKNOWN">UNKNOWN</option>
                <option value="INVALID_ARGUMENT">INVALID_ARGUMENT</option>
                <option value="DEADLINE_EXCEEDED">DEADLINE_EXCEEDED</option>
                <option value="NOT_FOUND">NOT_FOUND</option>
                <option value="ALREADY_EXISTS">ALREADY_EXISTS</option>
                <option value="PERMISSION_DENIED">PERMISSION_DENIED</option>
                <option value="UNAUTHENTICATED">UNAUTHENTICATED</option>
                <option value="RESOURCE_EXHAUSTED">RESOURCE_EXHAUSTED</option>
                <option value="FAILED_PRECONDITION">FAILED_PRECONDITION</option>
                <option value="ABORTED">ABORTED</option>
                <option value="OUT_OF_RANGE">OUT_OF_RANGE</option>
                <option value="UNIMPLEMENTED">UNIMPLEMENTED</option>
                <option value="INTERNAL">INTERNAL</option>
                <option value="UNAVAILABLE">UNAVAILABLE</option>
                <option value="DATA_LOSS">DATA_LOSS</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">呼叫類型</label>
              <select
                value={filters.callType}
                onChange={(e) => setFilters({...filters, callType: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="">全部</option>
                <option value="UNARY">UNARY</option>
                <option value="CLIENT_STREAMING">CLIENT_STREAMING</option>
                <option value="SERVER_STREAMING">SERVER_STREAMING</option>
                <option value="BIDI_STREAMING">BIDI_STREAMING</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">開始時間</label>
              <input
                type="datetime-local"
                value={filters.startTime}
                onChange={(e) => setFilters({...filters, startTime: e.target.value})}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              />
            </div>
          </div>
          <div className="flex justify-end space-x-3 mt-4">
            <button
              onClick={handleClearFilters}
              className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
            >
              清除過濾器
            </button>
            <button
              onClick={handleSearch}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
            >
              搜尋
            </button>
          </div>
        </div>
      )}

      {/* 表格 */}
      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('callStartTime')}
                >
                  <div className="flex items-center space-x-1">
                    <span>呼叫時間</span>
                    {sortBy === 'callStartTime' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('clientIp')}
                >
                  <div className="flex items-center space-x-1">
                    <span>客戶端 IP</span>
                    {sortBy === 'clientIp' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('targetLocation')}
                >
                  <div className="flex items-center space-x-1">
                    <span>目標位置</span>
                    {sortBy === 'targetLocation' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('methodName')}
                >
                  <div className="flex items-center space-x-1">
                    <span>呼叫方法</span>
                    {sortBy === 'methodName' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('executionTimeMs')}
                >
                  <div className="flex items-center space-x-1">
                    <span>執行時間</span>
                    {sortBy === 'executionTimeMs' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th 
                  className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider cursor-pointer hover:bg-gray-100"
                  onClick={() => handleSort('statusCode')}
                >
                  <div className="flex items-center space-x-1">
                    <span>狀態碼</span>
                    {sortBy === 'statusCode' && (
                      <span className={`transform transition-transform ${sortDirection === 'asc' ? 'rotate-180' : ''}`}>
                        ▼
                      </span>
                    )}
                  </div>
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  類型
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-gray-500">
                    沒有找到呼叫記錄
                  </td>
                </tr>
              ) : (
                logs.map((log, index) => (
                  <tr key={log.id || index} className="hover:bg-gray-50">
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {formatDateTime(log.callStartTime)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {log.clientIp}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {log.targetLocation}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      <span className="font-mono text-xs bg-gray-100 px-2 py-1 rounded">
                        {log.methodName}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      {formatDuration(log.executionTimeMs)}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(log.statusCode)}`}>
                        {log.statusCode}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                      <span className="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">
                        {log.callType}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 分頁 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <span className="text-sm text-gray-700">每頁顯示:</span>
            <select
              value={pageSize}
              onChange={(e) => {
                setPageSize(parseInt(e.target.value));
                setCurrentPage(0);
              }}
              className="px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
            >
              <option value={10}>10</option>
              <option value={20}>20</option>
              <option value={50}>50</option>
              <option value={100}>100</option>
            </select>
          </div>
          
          <div className="flex items-center space-x-2">
            <button
              onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
              disabled={currentPage === 0}
              className="px-3 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              上一頁
            </button>
            
            <span className="text-sm text-gray-700">
              第 {currentPage + 1} 頁，共 {totalPages} 頁
            </span>
            
            <button
              onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
              disabled={currentPage >= totalPages - 1}
              className="px-3 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              下一頁
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

// ===========================================
// 13. 主應用程式組件
// ===========================================
const App: React.FC = () => {
  const { user, loading } = useAuth();
  const [activeTab, setActiveTab] = useState('dashboard');

  if (loading) {
    return (
        <div className="min-h-screen bg-gray-50 flex items-center justify-center">
          <div className="text-center">
            <RefreshCw className="h-8 w-8 animate-spin text-blue-500 mx-auto mb-4" />
            <p className="text-gray-600">載入中...</p>
          </div>
        </div>
    );
  }

  if (!user) {
    return <LoginForm />;
  }

  const renderContent = () => {
    switch (activeTab) {
      case 'dashboard':
        return <Dashboard />;
      case 'proxies':
        return <ProxyManagement />;
      case 'profile':
        return <UserProfile />;
      case 'settings':
        return <SystemSettings />;
      case 'users':
        return <UserManagement />;
      case 'grpc-logs':
        return <GrpcCallLogs />;
      default:
        return <Dashboard />;
    }
  };

  return (
      <div className="flex h-screen bg-gray-50">
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />
        <main className="flex-1 overflow-y-auto">
          {renderContent()}
        </main>
      </div>
  );
};

// ===========================================
// 12. 根組件
// ===========================================
const GStreamGateApp: React.FC = () => {
  return (
      <AuthProvider>
        <App />
      </AuthProvider>
  );
};

export default GStreamGateApp;