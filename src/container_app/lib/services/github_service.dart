// MIT License
//
// Copyright (c) 2026 iappyx
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

/// Client for submitting and managing apps in the iappyxOS-showcase GitHub repo.

import 'dart:convert';
import 'package:http/http.dart' as http;

class GithubService {
  static const _repo = 'iappyxOS-showcase';
  static const _owner = 'iappyx';
  static const _api = 'https://api.github.com';

  final String _token;
  late final Map<String, String> _headers;
  String? _username;

  GithubService(this._token) {
    _headers = {
      'Authorization': 'token $_token',
      'Accept': 'application/vnd.github.v3+json',
      'Content-Type': 'application/json',
    };
  }

  Future<String> getUsername() async => _getUsername();

  Future<String> _getUsername() async {
    if (_username != null) return _username!;
    final resp = await http.get(Uri.parse('$_api/user'), headers: _headers);
    if (resp.statusCode != 200) throw Exception('Invalid GitHub token');
    _username = jsonDecode(resp.body)['login'];
    return _username!;
  }

  bool _isOwner() => _username == _owner;

  /// Submit an app to the showcase. Returns the PR URL.
  Future<String> submitApp({
    required String slug,
    required String appHtml,
    required String name,
    required String description,
    required String author,
    required List<String> bridges,
  }) async {
    final username = await _getUsername();
    final isOwner = _isOwner();
    final repoFullName = isOwner ? '$_owner/$_repo' : '$username/$_repo';
    final branch = 'showcase/$slug';

    // 1. Ensure fork exists (skip if owner)
    if (!isOwner) {
      await _ensureFork(username);
      await _syncFork(username);
    }

    // 2. Get main branch SHA
    final mainSha = await _getRef(repoFullName, 'heads/main');

    // 3. Delete branch if it already exists (update scenario)
    try { await _deleteRef(repoFullName, 'heads/$branch'); } catch (_) {}

    // 4. Create branch
    await _createRef(repoFullName, 'refs/heads/$branch', mainSha);

    // 5. Build file contents
    final showcaseJson = const JsonEncoder.withIndent('  ').convert({
      'name': name,
      'description': description,
      'author': author,
      'bridges': bridges,
      'added': DateTime.now().toIso8601String().substring(0, 10),
    });

    final readme = '# $name\n\n$description\n\n**Bridges used:** ${bridges.join(", ")}\n';

    // 6. Create tree with all files
    final baseTree = await _getTreeSha(repoFullName, mainSha);
    final treeSha = await _createTree(repoFullName, baseTree, [
      _TreeEntry('$slug/app.html', appHtml),
      _TreeEntry('$slug/showcase.json', showcaseJson),
      _TreeEntry('$slug/README.md', readme),
    ]);

    // 7. Create commit
    final commitSha = await _createCommit(
      repoFullName, 'Add showcase app: $name', treeSha, mainSha);

    // 8. Update branch ref
    await _updateRef(repoFullName, 'heads/$branch', commitSha);

    // 9. Create PR
    final prUrl = await _createPR(
      head: isOwner ? branch : '$username:$branch',
      title: 'Showcase: $name',
      body: '$description\n\n**Bridges:** ${bridges.join(", ")}\n\n**Author:** $author',
    );

    return prUrl;
  }

  Future<void> _ensureFork(String username) async {
    // Check if fork exists
    final check = await http.get(
      Uri.parse('$_api/repos/$username/$_repo'), headers: _headers);
    if (check.statusCode == 200) return; // already exists

    // Create fork
    final resp = await http.post(
      Uri.parse('$_api/repos/$_owner/$_repo/forks'),
      headers: _headers,
    );
    if (resp.statusCode != 202) throw Exception('Failed to fork: ${resp.statusCode}');

    // Poll until fork is ready (max 30s)
    for (int i = 0; i < 15; i++) {
      await Future.delayed(const Duration(seconds: 2));
      final poll = await http.get(
        Uri.parse('$_api/repos/$username/$_repo'), headers: _headers);
      if (poll.statusCode == 200) return;
    }
    throw Exception('Fork not ready after 30 seconds');
  }

  Future<void> _syncFork(String username) async {
    final resp = await http.post(
      Uri.parse('$_api/repos/$username/$_repo/merge-upstream'),
      headers: _headers,
      body: jsonEncode({'branch': 'main'}),
    );
    // 200 = synced, 409 = already up to date — both fine
    if (resp.statusCode != 200 && resp.statusCode != 409) {
      // Non-critical — PR may be based on slightly old code but still works
    }
  }

  Future<String> _getRef(String repo, String ref) async {
    // Retry a few times — newly forked repos may not have branches ready immediately
    for (int i = 0; i < 5; i++) {
      final resp = await http.get(
        Uri.parse('$_api/repos/$repo/git/ref/$ref'), headers: _headers);
      if (resp.statusCode == 200) return jsonDecode(resp.body)['object']['sha'];
      if (i < 4) await Future.delayed(const Duration(seconds: 2));
    }
    throw Exception('Failed to get ref $ref after retries');
  }

  Future<void> _createRef(String repo, String ref, String sha) async {
    final resp = await http.post(
      Uri.parse('$_api/repos/$repo/git/refs'),
      headers: _headers,
      body: jsonEncode({'ref': ref, 'sha': sha}),
    );
    if (resp.statusCode != 201) throw Exception('Failed to create ref: ${resp.statusCode}');
  }

  Future<void> _deleteRef(String repo, String ref) async {
    final resp = await http.delete(
      Uri.parse('$_api/repos/$repo/git/refs/$ref'), headers: _headers);
    if (resp.statusCode != 204) throw Exception('Failed to delete ref');
  }

  Future<void> _updateRef(String repo, String ref, String sha) async {
    final resp = await http.patch(
      Uri.parse('$_api/repos/$repo/git/refs/$ref'),
      headers: _headers,
      body: jsonEncode({'sha': sha, 'force': true}),
    );
    if (resp.statusCode != 200) throw Exception('Failed to update ref: ${resp.statusCode}');
  }

  Future<String> _getTreeSha(String repo, String commitSha) async {
    final resp = await http.get(
      Uri.parse('$_api/repos/$repo/git/commits/$commitSha'), headers: _headers);
    if (resp.statusCode != 200) throw Exception('Failed to get commit');
    return jsonDecode(resp.body)['tree']['sha'];
  }

  Future<String> _createTree(String repo, String baseTree, List<_TreeEntry> entries) async {
    final tree = entries.map((e) => {
      'path': e.path,
      'mode': '100644',
      'type': 'blob',
      'content': e.content,
    }).toList();

    final resp = await http.post(
      Uri.parse('$_api/repos/$repo/git/trees'),
      headers: _headers,
      body: jsonEncode({'base_tree': baseTree, 'tree': tree}),
    );
    if (resp.statusCode != 201) throw Exception('Failed to create tree: ${resp.statusCode}');
    return jsonDecode(resp.body)['sha'];
  }

  Future<String> _createCommit(String repo, String message, String treeSha, String parentSha) async {
    final resp = await http.post(
      Uri.parse('$_api/repos/$repo/git/commits'),
      headers: _headers,
      body: jsonEncode({'message': message, 'tree': treeSha, 'parents': [parentSha]}),
    );
    if (resp.statusCode != 201) throw Exception('Failed to create commit: ${resp.statusCode}');
    return jsonDecode(resp.body)['sha'];
  }

  Future<String> _createPR({required String head, required String title, required String body}) async {
    final resp = await http.post(
      Uri.parse('$_api/repos/$_owner/$_repo/pulls'),
      headers: _headers,
      body: jsonEncode({'title': title, 'body': body, 'head': head, 'base': 'main'}),
    );
    if (resp.statusCode == 422) {
      // PR already exists — find it
      final existing = await http.get(
        Uri.parse('$_api/repos/$_owner/$_repo/pulls?head=$head&state=open'),
        headers: _headers);
      if (existing.statusCode == 200) {
        final pulls = jsonDecode(existing.body) as List;
        if (pulls.isNotEmpty) return pulls[0]['html_url'];
      }
      throw Exception('PR already exists');
    }
    if (resp.statusCode != 201) throw Exception('Failed to create PR: ${resp.statusCode} ${resp.body}');
    return jsonDecode(resp.body)['html_url'];
  }
}

class _TreeEntry {
  final String path;
  final String content;
  _TreeEntry(this.path, this.content);
}
