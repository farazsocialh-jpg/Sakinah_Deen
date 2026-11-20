import XCTest

/// UI coverage for the SwiftUI shell: launch, tab navigation and the side drawer.
///
/// These run against the real app target (no stubs) and are intentionally limited to
/// navigation that works signed-out, so they never touch account, payment or Zakat flows.
final class SakinahUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    func testLaunchesOnSalahTabWithProductTitle() {
        XCTAssertTrue(
            app.tabBars.buttons["Salah"].waitForExistence(timeout: 30),
            "the tab bar must appear on launch"
        )
        XCTAssertTrue(app.staticTexts["ImanCore"].waitForExistence(timeout: 10))

        for tab in ["Salah", "Quran", "Hadith", "Safar", "Zakat", "Scholar"] {
            XCTAssertTrue(app.tabBars.buttons[tab].exists, "missing tab: \(tab)")
        }
    }

    func testSwitchingTabsShowsEachSection() {
        XCTAssertTrue(app.tabBars.buttons["Quran"].waitForExistence(timeout: 30))

        app.tabBars.buttons["Quran"].tap()
        XCTAssertTrue(
            app.staticTexts["Quran & Multi-Tafseer Studio"].waitForExistence(timeout: 10)
        )

        app.tabBars.buttons["Hadith"].tap()
        XCTAssertTrue(
            app.staticTexts["Nine Canonical Hadith Books"].waitForExistence(timeout: 10)
        )

        app.tabBars.buttons["Scholar"].tap()
        XCTAssertTrue(
            app.staticTexts["Islamic Scholar Research Assistant"].waitForExistence(timeout: 10)
        )

        app.tabBars.buttons["Salah"].tap()
        XCTAssertTrue(app.staticTexts["ImanCore"].waitForExistence(timeout: 10))
    }

    func testDrawerOpensSettingsAndDismisses() {
        let menu = app.buttons["Open menu"]
        XCTAssertTrue(menu.waitForExistence(timeout: 30), "the drawer button must be reachable")
        menu.tap()

        let settings = app.buttons["App Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 10), "the drawer must list App Settings")
        XCTAssertTrue(app.buttons["Privacy & Terms"].exists)
        XCTAssertTrue(app.buttons["About ImanCore"].exists)

        settings.tap()
        XCTAssertTrue(
            app.navigationBars["App Settings"].waitForExistence(timeout: 10),
            "tapping a drawer row must push that screen"
        )
        XCTAssertTrue(app.switches["Fajr"].exists || app.staticTexts["Fajr"].exists)

        app.buttons["Close"].tap()
        XCTAssertTrue(app.tabBars.buttons["Salah"].waitForExistence(timeout: 10))
    }

    func testGuestCanOpenTheAuthScreenFromTheDrawer() {
        let menu = app.buttons["Open menu"]
        XCTAssertTrue(menu.waitForExistence(timeout: 30))
        menu.tap()

        let signIn = app.buttons["Sign In / Create Account"]
        XCTAssertTrue(signIn.waitForExistence(timeout: 10))
        signIn.tap()

        XCTAssertTrue(
            app.buttons["Skip / Continue as Guest"].waitForExistence(timeout: 10),
            "the auth screen must offer a guest escape"
        )
        app.buttons["Skip / Continue as Guest"].tap()
        XCTAssertTrue(app.tabBars.buttons["Salah"].waitForExistence(timeout: 10))
    }
}
