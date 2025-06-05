import { useQuery, useMutation } from '@tanstack/react-query';
import { fetchProxies, refreshProxies } from '../api';
import { Button } from '@mui/material';

export default function ProxyMappings() {
  const { data: proxies = [], refetch } = useQuery(['proxies'], fetchProxies);
  const refresh = useMutation(refreshProxies, { onSuccess: () => refetch() });

  return (
    <div>
      <h2>Proxy Mappings</h2>
      <Button onClick={() => refresh.mutate()} disabled={refresh.isLoading}>
        Refresh Mappings
      </Button>
      <pre>{JSON.stringify(proxies, null, 2)}</pre>
    </div>
  );
}
