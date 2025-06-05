import { useState } from 'react';
import { Button, TextField } from '@mui/material';
import { login } from '../api';

export default function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const handleSubmit = async () => {
    try {
      const { data } = await login(username, password);
      onLogin(data.token);
    } catch {
      alert('Login failed');
    }
  };

  return (
    <div>
      <h1>Login</h1>
      <TextField label="Username" value={username} onChange={e => setUsername(e.target.value)} />
      <TextField label="Password" type="password" value={password} onChange={e => setPassword(e.target.value)} />
      <Button variant="contained" onClick={handleSubmit}>Sign In</Button>
    </div>
  );
}
