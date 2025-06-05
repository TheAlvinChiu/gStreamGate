import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

export const login = (username: string, password: string) =>
  api.post('/auth/login', { username, password });

export const fetchProxies = () => api.get('/proxy');

export const refreshProxies = () => api.post('/proxy/refresh');
