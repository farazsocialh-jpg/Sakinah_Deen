import SwiftUI

struct AuthView: View {
    @ObservedObject var store: AccountStore
    @Environment(\.dismiss) private var dismiss

    @State private var isCreateAccount = false
    @State private var email = ""
    @State private var password = ""
    @State private var displayName = ""
    @State private var showReset = false
    @State private var resetEmail = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Picker("Mode", selection: $isCreateAccount) {
                    Text("Sign In").tag(false)
                    Text("Create Account").tag(true)
                }
                .pickerStyle(.segmented)

                TextField("Email", text: $email)
                    .textInputAutocapitalization(.never)
                    .keyboardType(.emailAddress)
                    .textFieldStyle(.roundedBorder)

                SecureField("Password", text: $password)
                    .textFieldStyle(.roundedBorder)

                if isCreateAccount {
                    TextField("Display name", text: $displayName)
                        .textFieldStyle(.roundedBorder)
                }

                if let error = store.errorMessage {
                    Text(error).font(.caption).foregroundColor(.red)
                }

                Button {
                    if isCreateAccount {
                        store.createAccount(email: email, password: password, displayName: displayName)
                    } else {
                        store.signIn(email: email, password: password)
                    }
                    dismiss()
                } label: {
                    Text(isCreateAccount ? "Create Account" : "Sign In")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .background(Color.emeraldDeep)
                .foregroundColor(.white)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                Button("Forgot password?") {
                    resetEmail = email
                    showReset = true
                }
                .font(.footnote)

                Divider()

                Button {
                    store.signInWithGoogle()
                    dismiss()
                } label: {
                    Label("Continue with Google", systemImage: "globe")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.emeraldDeep))

                Button {
                    store.signInWithApple()
                    dismiss()
                } label: {
                    Label("Continue with Apple", systemImage: "apple.logo")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.emeraldDeep))

                Button("Skip / Continue as Guest") {
                    store.continueAsGuest()
                    dismiss()
                }
                .frame(maxWidth: .infinity)
                .font(.footnote.bold())
            }
            .padding()
            .frame(maxWidth: 600)
        }
        .background(Color.warmCream.ignoresSafeArea())
        .navigationTitle(isCreateAccount ? "Create Account" : "Sign In")
        .alert("Reset password", isPresented: $showReset) {
            TextField("Email", text: $resetEmail)
                .textInputAutocapitalization(.never)
            Button("Send") { store.resetPassword(email: resetEmail) }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("We will email a reset link if the account exists.")
        }
    }
}
