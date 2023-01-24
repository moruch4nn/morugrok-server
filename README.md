# morugrok-server
自作ngrokのサーバーです。

環境変数にJWT Token用のシークレットを設定しておく必要があります。 `SECRET`

特にデータ保存機能などもないのでJWTTokenのSECRETの設定のみで動作します。

※JWTTokenの自動生成機能はないため各自で設定してください。<br>
詳細
```
Algorithm: HS512
Secret: ENV("SECRET")
Payload: 
{
  "name": "username", # username
   "perm": [ # permission
      "port.select", # can specify the port
      "con.new.tcp" # can create a new tcp connection
   ],
   "iss": "iss", # issuer
   "aud": "aud", # audience
   "exp": 1000000000000000000 # expiration Time
}
```
