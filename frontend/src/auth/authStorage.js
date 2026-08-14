const TOKEN_KEY='enterprise_access_token'
export const authStorage={getToken:()=>localStorage.getItem(TOKEN_KEY),setToken:(token)=>localStorage.setItem(TOKEN_KEY,token),clear:()=>localStorage.removeItem(TOKEN_KEY)}
