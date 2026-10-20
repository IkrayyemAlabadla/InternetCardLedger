package com.alabadla.internetcardledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alabadla.internetcardledger.LedgerApplication
import com.alabadla.internetcardledger.R
import com.alabadla.internetcardledger.ui.screen.BackupScreen
import com.alabadla.internetcardledger.ui.screen.ConflictReviewScreen
import com.alabadla.internetcardledger.ui.screen.CustomersScreen
import com.alabadla.internetcardledger.ui.screen.HistoryScreen
import com.alabadla.internetcardledger.ui.screen.HomeScreen
import com.alabadla.internetcardledger.ui.screen.PlaceholderScreen
import com.alabadla.internetcardledger.ui.screen.SalesScreen
import com.alabadla.internetcardledger.ui.screen.SyncScreen
import com.alabadla.internetcardledger.ui.theme.InternetCardLedgerTheme
import com.alabadla.internetcardledger.ui.viewmodel.BackupViewModel
import com.alabadla.internetcardledger.ui.viewmodel.CustomersViewModel
import com.alabadla.internetcardledger.ui.viewmodel.DashboardViewModel
import com.alabadla.internetcardledger.ui.viewmodel.SalesViewModel
import com.alabadla.internetcardledger.ui.viewmodel.SyncViewModel

private object Routes {
    const val HOME = "home"
    const val NEW_SALE = "new_sale"
    const val CUSTOMERS = "customers"
    const val HISTORY = "history"
    const val SALE_DETAILS = "sale_details"
    const val CUSTOMER_DETAILS = "customer_details"
    const val SYNC = "sync"
    const val CONFLICT_REVIEW = "conflict_review"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
}

@Composable
fun InternetCardLedgerApp() {
    InternetCardLedgerTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LedgerNavigation(navController = rememberNavController())
        }
    }
}

@Composable
private fun LedgerNavigation(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.factory(
                    application.container.customerRepository,
                    application.container.saleRepository,
                    application.container.paymentRepository,
                ),
            )
            val dashboardState by dashboardViewModel.dashboardState.collectAsStateWithLifecycle()
            HomeScreen(
                dashboardState = dashboardState,
                onNewSale = { navController.navigate(Routes.NEW_SALE) },
                onCustomers = { navController.navigate(Routes.CUSTOMERS) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onBackup = { navController.navigate(Routes.BACKUP) },
                onSync = { navController.navigate(Routes.SYNC) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NEW_SALE) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val salesViewModel: SalesViewModel = viewModel(
                factory = SalesViewModel.factory(
                    application.container.saleRepository,
                    application.container.customerRepository,
                ),
            )
            SalesScreen(viewModel = salesViewModel, onBack = navController::navigateUp)
        }
        composable(Routes.CUSTOMERS) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val customersViewModel: CustomersViewModel = viewModel(
                factory = CustomersViewModel.factory(
                    application.container.customerRepository,
                    application.container.saleRepository,
                    application.container.paymentRepository,
                ),
            )
            CustomersScreen(viewModel = customersViewModel, onBack = navController::navigateUp)
        }
        composable(Routes.HISTORY) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val dashboardViewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.factory(
                    application.container.customerRepository,
                    application.container.saleRepository,
                    application.container.paymentRepository,
                ),
            )
            HistoryScreen(
                viewModel = dashboardViewModel,
                onBack = navController::navigateUp,
                onOpenSale = { id -> navController.navigate("${Routes.SALE_DETAILS}/$id") },
                onOpenCustomer = { id -> navController.navigate("${Routes.CUSTOMER_DETAILS}/$id") },
            )
        }
        composable("${Routes.SALE_DETAILS}/{recordId}") { entry ->
            val application = LocalContext.current.applicationContext as LedgerApplication
            val salesViewModel: SalesViewModel = viewModel(
                factory = SalesViewModel.factory(
                    application.container.saleRepository,
                    application.container.customerRepository,
                ),
            )
            SalesScreen(
                viewModel = salesViewModel,
                onBack = navController::navigateUp,
                initialSaleId = entry.arguments?.getString("recordId"),
            )
        }
        composable("${Routes.CUSTOMER_DETAILS}/{recordId}") { entry ->
            val application = LocalContext.current.applicationContext as LedgerApplication
            val customersViewModel: CustomersViewModel = viewModel(
                factory = CustomersViewModel.factory(
                    application.container.customerRepository,
                    application.container.saleRepository,
                    application.container.paymentRepository,
                ),
            )
            CustomersScreen(
                viewModel = customersViewModel,
                onBack = navController::navigateUp,
                initialCustomerId = entry.arguments?.getString("recordId"),
            )
        }
        composable(Routes.SYNC) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val syncViewModel: SyncViewModel = viewModel(
                factory = SyncViewModel.factory(
                    syncEngine = application.container.syncEngine,
                    syncDao = application.container.database.syncDao(),
                    approvedDeviceDao = application.container.database.approvedDeviceDao(),
                    transport = application.container.syncTransport,
                ),
            )
            SyncScreen(
                viewModel = syncViewModel,
                onOpenConflictReview = { navController.navigate(Routes.CONFLICT_REVIEW) },
                onBack = navController::navigateUp,
            )
        }
        composable(Routes.CONFLICT_REVIEW) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val syncViewModel: SyncViewModel = viewModel(
                factory = SyncViewModel.factory(
                    syncEngine = application.container.syncEngine,
                    syncDao = application.container.database.syncDao(),
                    approvedDeviceDao = application.container.database.approvedDeviceDao(),
                    transport = application.container.syncTransport,
                ),
            )
            val unresolvedConflicts by syncViewModel.unresolvedConflicts.collectAsStateWithLifecycle()
            ConflictReviewScreen(
                conflicts = unresolvedConflicts,
                onAcceptMain = syncViewModel::acceptMainVersion,
                onAcceptSecondary = syncViewModel::acceptSecondaryVersion,
                onBack = navController::navigateUp,
            )
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                titleResource = R.string.action_settings,
                messageResource = R.string.settings_placeholder,
                icon = Icons.Default.Settings,
                iconDescriptionResource = R.string.icon_settings_description,
                onBack = navController::navigateUp,
            )
        }
        composable(Routes.BACKUP) {
            val application = LocalContext.current.applicationContext as LedgerApplication
            val backupViewModel: BackupViewModel = viewModel(
                factory = BackupViewModel.factory(application.container.backupRepository),
            )
            BackupScreen(viewModel = backupViewModel, onBack = navController::navigateUp)
        }
    }
}
