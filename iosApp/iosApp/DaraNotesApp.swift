// The iOS entry point: shows the shared Kotlin UI (the DaraUI framework built from :ui).
import DaraUI
import SwiftUI

@main
struct DaraNotesApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Background sync must be registered before the app finishes launching.
        MainViewControllerKt.startBackgroundSync()
    }

    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and applies the safe-area insets itself.
            ComposeView().ignoresSafeArea()
        }
        .onChange(of: scenePhase) { _, phase in
            // Pull changes made elsewhere, as the Android app does when it opens; push edits
            // not yet synced when it goes to the background.
            if phase == .active { MainViewControllerKt.onForeground() }
            if phase == .background { MainViewControllerKt.onBackground() }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
