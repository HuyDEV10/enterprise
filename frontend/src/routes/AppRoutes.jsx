import { Navigate, Route, Routes } from 'react-router-dom'
import MainLayout from '../layouts/MainLayout'
import ProtectedRoute from '../auth/ProtectedRoute'
import RoleProtectedRoute from '../auth/RoleProtectedRoute'
import LoginPage from '../pages/LoginPage'
import AccessDeniedPage from '../pages/AccessDeniedPage'
import UsersPage from '../pages/UsersPage'
import DashboardPage from '../pages/DashboardPage'
import SupplierListPage from '../pages/SupplierListPage'
import SupplierFormPage from '../pages/SupplierFormPage'
import SupplierDetailPage from '../pages/SupplierDetailPage'
import ProductListPage from '../pages/ProductListPage'
import ProductFormPage from '../pages/ProductFormPage'
import CategoryPage from '../pages/CategoryPage'
import WarehouseListPage from '../pages/WarehouseListPage'
import WarehouseFormPage from '../pages/WarehouseFormPage'
import InventoryPage from '../pages/InventoryPage'
import InventoryFormPage from '../pages/InventoryFormPage'
import PurchaseOrderListPage from '../pages/PurchaseOrderListPage'
import PurchaseOrderFormPage from '../pages/PurchaseOrderFormPage'
import PurchaseOrderDetailPage from '../pages/PurchaseOrderDetailPage'
import ShipmentListPage from '../pages/ShipmentListPage'
import ShipmentFormPage from '../pages/ShipmentFormPage'
import ShipmentDetailPage from '../pages/ShipmentDetailPage'
import RiskListPage from '../pages/RiskListPage'
import RiskFormPage from '../pages/RiskFormPage'
import RiskDetailPage from '../pages/RiskDetailPage'
import RiskMonitoringPage from '../pages/RiskMonitoringPage'
import AiForecastPage from '../pages/AiForecastPage'
import ExternalEventsPage from '../pages/ExternalEventsPage'
import AlertPage from '../pages/AlertPage'
import AlertFormPage from '../pages/AlertFormPage'
import CompanyPage from '../pages/CompanyPage'
import NotFoundPage from '../pages/NotFoundPage'

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/access-denied" element={<AccessDeniedPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<MainLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/risk-monitoring" element={<RiskMonitoringPage />} />
          <Route path="/ai-forecast" element={<AiForecastPage />} />
          <Route path="/external-events" element={<ExternalEventsPage />} />
          <Route path="/suppliers" element={<SupplierListPage />} />
          <Route
            path="/suppliers/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <SupplierFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/suppliers/:id" element={<SupplierDetailPage />} />
          <Route
            path="/suppliers/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <SupplierFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/products" element={<ProductListPage />} />
          <Route
            path="/products/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <ProductFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/products/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <ProductFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/product-categories" element={<CategoryPage />} />
          <Route path="/warehouses" element={<WarehouseListPage />} />
          <Route
            path="/warehouses/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <WarehouseFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/warehouses/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <WarehouseFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route
            path="/inventory/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <InventoryFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/inventory/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <InventoryFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/purchase-orders" element={<PurchaseOrderListPage />} />
          <Route
            path="/purchase-orders/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <PurchaseOrderFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/purchase-orders/:id"
            element={<PurchaseOrderDetailPage />}
          />
          <Route
            path="/purchase-orders/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <PurchaseOrderFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/shipments" element={<ShipmentListPage />} />
          <Route
            path="/shipments/new"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <ShipmentFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route path="/shipments/:id" element={<ShipmentDetailPage />} />
          <Route
            path="/shipments/:id/edit"
            element={
              <RoleProtectedRoute permission="writeOperations">
                <ShipmentFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/risks"
            element={
              <RoleProtectedRoute permission="readRisks">
                <RiskListPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/risks/new"
            element={
              <RoleProtectedRoute permission="writeRisks">
                <RiskFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/risks/:id"
            element={
              <RoleProtectedRoute permission="readRisks">
                <RiskDetailPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/risks/:id/edit"
            element={
              <RoleProtectedRoute permission="writeRisks">
                <RiskFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/alerts"
            element={
              <RoleProtectedRoute permission="readAlerts">
                <AlertPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/alerts/new"
            element={
              <RoleProtectedRoute permission="createAlerts">
                <AlertFormPage />
              </RoleProtectedRoute>
            }
          />
          <Route
            path="/company"
            element={
              <RoleProtectedRoute permission="readCompany">
                <CompanyPage />
              </RoleProtectedRoute>
            }
          />
          <Route element={<RoleProtectedRoute roles={['ADMIN']} />}>
            <Route path="/users" element={<UsersPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
