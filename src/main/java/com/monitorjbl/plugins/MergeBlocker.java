package com.monitorjbl.plugins;

import com.atlassian.bitbucket.pull.PullRequest;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scm.pull.MergeRequest;
import com.atlassian.bitbucket.scm.pull.MergeRequestCheck;
import com.atlassian.bitbucket.user.ApplicationUser;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.google.common.base.Joiner;
import com.monitorjbl.plugins.config.Config;
import com.monitorjbl.plugins.config.ConfigDao;

import javax.annotation.Nonnull;
import java.util.Set;

public class MergeBlocker implements MergeRequestCheck {
  private final ConfigDao configDao;
  private final UserUtils userUtils;
  private final RegexUtils regexUtils;
  private final UserManager userManager;

  public MergeBlocker(ConfigDao configDao, UserManager userManager, UserUtils userUtils, RegexUtils regexUtils) {
    this.configDao = configDao;
    this.userUtils = userUtils;
    this.regexUtils = regexUtils;
    this.userManager = userManager;
  }

  @Override
  public void check(@Nonnull MergeRequest mergeRequest) {
    PullRequest pr = mergeRequest.getPullRequest();
    Repository repo = pr.getToRef().getRepository();
    final Config config = configDao.getConfigForRepo(repo.getProject().getKey(), repo.getSlug());

    String branch = regexUtils.formatBranchName(pr.getToRef().getId());
    if (regexUtils.match(config.getBlockedPRs(), branch)) {
      mergeRequest.veto("Pull Request Blocked", "Pull requests have been disabled for branch [" + branch + "]");
    } else {
      PullRequestApproval approval = new PullRequestApproval(config, userUtils);

      UserProfile remoteUser;
      if(userManager != null) {
        remoteUser = userManager.getRemoteUser();
      }
      else {
        remoteUser = null;
      }
      boolean approved;
      if(remoteUser != null) {
        ApplicationUser applicationUser = userUtils.getApplicationUserByName(remoteUser.getUsername());
        approved = approval.isPullRequestApproved(pr, applicationUser);
      }
      else {
        approved = approval.isPullRequestApproved(pr);
      }

      if (!approved) {
        Set<String> missing = approval.missingRevieiwers(pr);
        mergeRequest.veto("Required reviewers must approve", (config.getRequiredReviews() - approval.seenReviewers(pr).size()) +
            " more approvals required from the following users: " + Joiner.on(',').join(missing));
      }
    }
  }

}
