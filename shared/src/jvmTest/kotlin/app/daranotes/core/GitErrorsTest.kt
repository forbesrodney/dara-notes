package app.daranotes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.UnknownHostException

class GitErrorsTest {
    private val url = "https://github.com/you/notes.git"

    @Test fun refusedAccess() = assertEquals(
        GitErrors.REFUSED,
        GitErrors.hint("$url: git-upload-pack not permitted on '$url/'"),
    )

    @Test fun refusedPush() = assertEquals(GitErrors.REFUSED, GitErrors.hint("$url: git-receive-pack not permitted"))

    @Test fun badToken() = assertEquals(GitErrors.SIGN_IN, GitErrors.hint("$url: not authorized"))

    @Test fun noToken() = assertEquals(
        GitErrors.SIGN_IN,
        GitErrors.hint("$url: Authentication is required but no CredentialsProvider has been registered"),
    )

    @Test fun missingRepo() = assertEquals(GitErrors.NOT_FOUND, GitErrors.hint("$url: repository not found"))

    @Test fun missingBranch() = assertEquals(
        GitErrors.BRANCH,
        GitErrors.hint("Remote branch 'mian' not found in upstream origin"),
    )

    @Test fun offlineFromCause() = assertEquals(
        GitErrors.OFFLINE,
        GitErrors.hint(RuntimeException("$url: cannot open git-upload-pack", UnknownHostException("github.com"))),
    )

    @Test fun unknownErrorHasNoHint() = assertNull(GitErrors.hint("Checkout conflict with files: Index.md"))
}
