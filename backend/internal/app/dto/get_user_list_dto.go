package dto

type GetUsersList struct {
	Limit  int `json:"limit"`
	Offset int `json:"offset"`
}

type GetUsersListResponse struct {
	Items []GetUserResponse `json:"items"`
}
