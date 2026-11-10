// The iOS entry point: shows the shared Kotlin UI (the FoamUI framework built from :ui).
import FoamUI
import SwiftUI

@main
struct FoamApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            // Compose draws edge to edge and applies the safe-area insets itself.
            ComposeView().ignoresSafeArea()
        }
        .onChange(of: scenePhase) { _, phase in
            // Pull changes made elsewhere, as the Android app does when it opens.
            if phase == .active { MainViewControllerKt.onForeground() }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
