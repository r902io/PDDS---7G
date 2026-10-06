import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { masterDataService, Customer } from '../services/masterDataService';
import { Order, RoadBlock, Vehicle, Warehouse } from '../types/logistics';

export function useMasterData() {
  const queryClient = useQueryClient();

  // Orders
  const { data: orders = [], isLoading: isLoadingOrders } = useQuery<Order[]>({
    queryKey: ['masterOrders'],
    queryFn: () => masterDataService.getOrders(),
  });

  const createOrderMutation = useMutation({
    mutationFn: (order: Partial<Order>) => masterDataService.createOrder(order),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masterOrders'] });
      queryClient.invalidateQueries({ queryKey: ['simulationState'] });
    },
  });

  // Customers
  const { data: customers = [], isLoading: isLoadingCustomers } = useQuery<Customer[]>({
    queryKey: ['masterCustomers'],
    queryFn: () => masterDataService.getCustomers(),
  });

  const createCustomerMutation = useMutation({
    mutationFn: (cust: Omit<Customer, 'id' | 'activeOrdersCount'>) =>
      masterDataService.createCustomer(cust),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masterCustomers'] });
    },
  });

  // Vehicles
  const { data: vehicles = [], isLoading: isLoadingVehicles } = useQuery<Vehicle[]>({
    queryKey: ['masterVehicles'],
    queryFn: () => masterDataService.getVehicles(),
  });

  // Warehouses
  const { data: warehouses = [], isLoading: isLoadingWarehouses } = useQuery<Warehouse[]>({
    queryKey: ['masterWarehouses'],
    queryFn: () => masterDataService.getWarehouses(),
  });

  // Road blocks
  const { data: roadBlocks = [], isLoading: isLoadingRoadBlocks } = useQuery<RoadBlock[]>({
    queryKey: ['masterRoadBlocks'],
    queryFn: () => masterDataService.getRoadBlocks(),
  });

  const createRoadBlockMutation = useMutation({
    mutationFn: (block: Omit<RoadBlock, 'id'>) => masterDataService.createRoadBlock(block),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masterRoadBlocks'] });
      queryClient.invalidateQueries({ queryKey: ['simulationState'] });
    },
  });

  const uploadHistoricosMutation = useMutation({
    mutationFn: (files: FileList | File[]) => masterDataService.uploadHistoricos(files),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masterOrders'] });
      queryClient.invalidateQueries({ queryKey: ['simulationState'] });
    },
  });

  return {
    orders,
    isLoadingOrders,
    createOrder: createOrderMutation.mutate,
    customers,
    isLoadingCustomers,
    createCustomer: createCustomerMutation.mutate,
    vehicles,
    isLoadingVehicles,
    warehouses,
    isLoadingWarehouses,
    roadBlocks,
    isLoadingRoadBlocks,
    createRoadBlock: createRoadBlockMutation.mutate,
    uploadHistoricos: uploadHistoricosMutation.mutate,
    uploadHistoricosAsync: uploadHistoricosMutation.mutateAsync,
    isUploadingHistoricos: uploadHistoricosMutation.isPending,
    uploadHistoricosError: uploadHistoricosMutation.error,
  };
}
