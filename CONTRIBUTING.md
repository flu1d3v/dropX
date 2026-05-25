\# Contributing



\## Reporting bugs

Open a GitHub issue. Include Android version, steps to reproduce,

and what you expected vs what happened.



\## Suggesting features

Open an issue with the label "enhancement" and describe the use case.



\## Code contributions

1\. Fork the repo

2\. Create a branch named after what you're changing

&#x20;  (e.g. `add-upload-support` or `fix-null-ip-crash`)

3\. Make your changes

4\. Open a Pull Request describing what you changed and why



\## Main open problem



\*\*Active MITM on local network with a plain browser receiver\*\*



dropX runs over HTTP. HTTPS doesn't actually fix this — self-signed

certs throw browser warnings that break the zero-friction receiver

experience, and even if the user clicks through they have no way to

verify server identity. A CA-signed cert is not an option on a local

network. So the trust problem exists regardless of whether you add

TLS or not.



An active attacker on the same network can intercept and proxy the

connection transparently. The session ID doesn't help — they're not

guessing URLs, they're in the path.



A proper fix needs a custom first-trust mechanism that works entirely

within a plain browser, without requiring the receiver to install

anything. No known clean solution to this exists within that

constraint. If you have an idea, open an issue.



\## Other useful additions

\- Upload from receiver to phone

\- SHA-256 checksum response headers for file integrity verification

