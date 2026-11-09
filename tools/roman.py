"""Kannada -> plain a-z romanization for the MMS forced aligner."""
V = {'ಅ':'a','ಆ':'a','ಇ':'i','ಈ':'i','ಉ':'u','ಊ':'u','ಋ':'ru','ೠ':'ru','ಎ':'e','ಏ':'e','ಐ':'ai','ಒ':'o','ಓ':'o','ಔ':'au'}
S = {'ಾ':'a','ಿ':'i','ೀ':'i','ು':'u','ೂ':'u','ೃ':'ru','ೄ':'ru','ೆ':'e','ೇ':'e','ೈ':'ai','ೊ':'o','ೋ':'o','ೌ':'au'}
C = {'ಕ':'k','ಖ':'kh','ಗ':'g','ಘ':'gh','ಙ':'n','ಚ':'ch','ಛ':'ch','ಜ':'j','ಝ':'jh','ಞ':'n','ಟ':'t','ಠ':'th','ಡ':'d','ಢ':'dh','ಣ':'n',
     'ತ':'t','ಥ':'th','ದ':'d','ಧ':'dh','ನ':'n','ಪ':'p','ಫ':'ph','ಬ':'b','ಭ':'bh','ಮ':'m','ಯ':'y','ರ':'r','ಱ':'r','ಲ':'l','ವ':'v',
     'ಶ':'sh','ಷ':'sh','ಸ':'s','ಹ':'h','ಳ':'l','ೞ':'l'}
def roman(w):
    out = []; i = 0
    while i < len(w):
        ch = w[i]
        if ch in C:
            out.append(C[ch]); nxt = w[i+1] if i+1 < len(w) else ''
            if nxt == '್': i += 2; continue
            if nxt in S: out.append(S[nxt]); i += 2; continue
            out.append('a'); i += 1; continue
        if ch in V: out.append(V[ch])
        elif ch == 'ಂ': out.append('m')
        elif ch == 'ಃ': out.append('h')
        i += 1
    return ''.join(out)
