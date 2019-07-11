package au.gov.api.ingest

import org.junit.Assert
import org.junit.Test

import au.gov.api.ingest.GitHub

class GitHubTest{

    var ghapi:GitHub = GitHub()

    @Test
    fun can_get_raw_github_uri_from_actual_uri(){

        val actualURI = "https://github.com/apigovau/api-gov-au-definitions/blob/master/api-documentation.md"
        val rawURI = "https://raw.githubusercontent.com/apigovau/api-gov-au-definitions/master/api-documentation.md"
        Assert.assertEquals(rawURI, GitHub.getRawURI(actualURI))
    }


    @Test
    fun can_get_text_of_github_file(){
        val uri="https://github.com/octocat/hello-worId/blob/master/README.md"
        val expectedContents = """hello-worId
===========

My first repository on GitHub.
"""

        Assert.assertEquals(expectedContents, GitHub.getTextOfFlie(uri))
    }

    @Test
    fun can_get_username_from_github_uri() {
        var username = GitHub.getUserGitHubUri("https://github.com/apigovau/service-catalogue-repository/blob/master/README.md")
        Assert.assertEquals(username,"apigovau")
    }


    @Test
    fun can_get_reponame_from_github_uri() {
        var reponame = GitHub.getRepoGitHubUri("https://github.com/apigovau/service-catalogue-repository/blob/master/README.md")
        Assert.assertEquals(reponame,"service-catalogue-repository")
    }
}
