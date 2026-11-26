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
        ShortcutsController(child: MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}

/// Holds the Compose UI and adds Cmd+N (new note) and Cmd+D (today's note) for a hardware
/// keyboard, from any screen, as in the desktop app. Key commands are found along the
/// responder chain, so they work wherever the focus is, and iPadOS lists them when you
/// hold Cmd.
final class ShortcutsController: UIViewController {
    private let child: UIViewController

    init(child: UIViewController) {
        self.child = child
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("not used") }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(child)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(child.view)
        child.didMove(toParent: self)
    }

    // The Compose UI still decides the status bar and home indicator.
    override var childForStatusBarStyle: UIViewController? { child }
    override var childForStatusBarHidden: UIViewController? { child }
    override var childForHomeIndicatorAutoHidden: UIViewController? { child }

    override var keyCommands: [UIKeyCommand]? {
        [
            UIKeyCommand(title: "New Note", action: #selector(shortcutNewNote), input: "n", modifierFlags: .command),
            UIKeyCommand(title: "Today's Note", action: #selector(shortcutToday), input: "d", modifierFlags: .command),
            UIKeyCommand(title: "Back", action: #selector(shortcutBack), input: "[", modifierFlags: .command),
            // Esc goes Back too, as on the desktop and Android. Untitled, so the Cmd list
            // shows Back once.
            UIKeyCommand(input: UIKeyCommand.inputEscape, modifierFlags: [], action: #selector(shortcutBack)),
        ]
    }

    @objc private func shortcutNewNote() { MainViewControllerKt.shortcutNewNote() }
    @objc private func shortcutToday() { MainViewControllerKt.shortcutToday() }
    @objc private func shortcutBack() { MainViewControllerKt.shortcutBack() }
}
