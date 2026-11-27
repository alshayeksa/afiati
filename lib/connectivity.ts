import * as Network from 'expo-network';

export async function isInternetAvailable() {
  const state = await Network.getNetworkStateAsync();
  return state.isConnected === true && state.isInternetReachable !== false;
}

export const onNetworkChange = (listener: (online: boolean) => void) =>
  Network.addNetworkStateListener((state) => {
    listener(state.isConnected === true && state.isInternetReachable !== false);
  });

