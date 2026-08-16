import { useEffect, useState } from 'react'
import { userApi } from '../api/userApi'
import { getApiErrorMessage } from '../api/apiClient'

const initialForm = {
    username: '',
    fullName: '',
    email: '',
    password: '',
    role: 'STAFF',
}

export default function UsersPage() {
    const [users, setUsers] = useState([])
    const [roles, setRoles] = useState([])
    const [form, setForm] = useState(initialForm)
    const [error, setError] = useState('')
    const [message, setMessage] = useState('')
    const [saving, setSaving] = useState(false)

    async function load() {
        try {
            setError('')

            const [userData, roleData] = await Promise.all([
                userApi.all(),
                userApi.roles(),
            ])

            setUsers(userData)
            setRoles(roleData)
        } catch (e) {
            setError(getApiErrorMessage(e))
        }
    }

    useEffect(() => {
        load()
    }, [])

    function handleChange(e) {
        const { name, value } = e.target

        setForm((prev) => ({
            ...prev,
            [name]: value,
        }))
    }

    async function handleCreate(e) {
        e.preventDefault()

        try {
            setSaving(true)
            setError('')
            setMessage('')

            await userApi.create({
                username: form.username.trim(),
                fullName: form.fullName.trim(),
                email: form.email.trim(),
                password: form.password,
                roles: [form.role],
            })

            setForm(initialForm)
            setMessage('Tạo tài khoản thành công.')

            await load()
        } catch (e) {
            setError(getApiErrorMessage(e))
        } finally {
            setSaving(false)
        }
    }

    async function handleStatus(user) {
        const newStatus =
            user.status === 'ACTIVE'
                ? 'INACTIVE'
                : 'ACTIVE'

        try {
            setError('')
            setMessage('')

            await userApi.setStatus(user.id, newStatus)

            setMessage(
                newStatus === 'ACTIVE'
                    ? `Đã kích hoạt tài khoản ${user.username}.`
                    : `Đã khóa tài khoản ${user.username}.`
            )

            await load()
        } catch (e) {
            setError(getApiErrorMessage(e))
        }
    }

    async function handleRole(user, role) {
        try {
            setError('')
            setMessage('')

            await userApi.setRoles(user.id, [role])

            setMessage(
                `Đã cập nhật quyền ${user.username} thành ${role}.`
            )

            await load()
        } catch (e) {
            setError(getApiErrorMessage(e))
        }
    }

    return (
        <section>
            <div className="page-header">
                <div>
                    <h1>Người dùng & phân quyền</h1>
                    <p>Quản lý tài khoản và vai trò hệ thống.</p>
                </div>
            </div>

            {error && (
                <div className="alert alert-danger">
                    {error}
                </div>
            )}

            {message && (
                <div
                    className="alert"
                    style={{
                        background: '#ecfdf3',
                        border: '1px solid #abefc6',
                        color: '#067647',
                    }}
                >
                    {message}
                </div>
            )}

            <div className="panel form-panel" style={{ marginBottom: 20 }}>
                <h2>Tạo tài khoản mới</h2>

                <form onSubmit={handleCreate}>
                    <div className="form-grid" style={{ marginTop: 18 }}>
                        <label className="form-field">
                            <span className="form-label">
                                Tên đăng nhập <em>*</em>
                            </span>

                            <input
                                name="username"
                                value={form.username}
                                onChange={handleChange}
                                required
                                placeholder="Ví dụ: manager01"
                            />
                        </label>

                        <label className="form-field">
                            <span className="form-label">
                                Họ tên <em>*</em>
                            </span>

                            <input
                                name="fullName"
                                value={form.fullName}
                                onChange={handleChange}
                                required
                                placeholder="Nguyễn Văn A"
                            />
                        </label>

                        <label className="form-field">
                            <span className="form-label">
                                Email <em>*</em>
                            </span>

                            <input
                                type="email"
                                name="email"
                                value={form.email}
                                onChange={handleChange}
                                required
                                placeholder="user@example.com"
                            />
                        </label>

                        <label className="form-field">
                            <span className="form-label">
                                Mật khẩu <em>*</em>
                            </span>

                            <input
                                type="password"
                                name="password"
                                value={form.password}
                                onChange={handleChange}
                                required
                                minLength={8}
                                placeholder="Tối thiểu 8 ký tự"
                            />
                        </label>

                        <label className="form-field">
                            <span className="form-label">
                                Vai trò <em>*</em>
                            </span>

                            <select
                                name="role"
                                value={form.role}
                                onChange={handleChange}
                            >
                                {roles.map((role) => (
                                    <option
                                        key={role.id}
                                        value={role.name}
                                    >
                                        {role.name}
                                    </option>
                                ))}
                            </select>
                        </label>
                    </div>

                    <div className="form-actions">
                        <button
                            type="submit"
                            className="btn btn-primary"
                            disabled={saving}
                        >
                            {saving ? 'Đang tạo...' : 'Tạo tài khoản'}
                        </button>
                    </div>
                </form>
            </div>

            <div className="panel">
                <div className="section-heading">
                    <div>
                        <h2>Danh sách tài khoản</h2>
                        <p>{users.length} tài khoản trong hệ thống.</p>
                    </div>
                </div>

                <div className="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Tài khoản</th>
                                <th>Họ tên</th>
                                <th>Email</th>
                                <th>Vai trò</th>
                                <th>Trạng thái</th>
                                <th>Thao tác</th>
                            </tr>
                        </thead>

                        <tbody>
                            {users.map((user) => (
                                <tr key={user.id}>
                                    <td className="strong">
                                        {user.username}
                                    </td>

                                    <td>{user.fullName}</td>

                                    <td>{user.email}</td>

                                    <td>
                                        <select
                                            value={user.roles?.[0] || ''}
                                            onChange={(e) =>
                                                handleRole(user, e.target.value)
                                            }
                                        >
                                            {roles.map((role) => (
                                                <option
                                                    key={role.id}
                                                    value={role.name}
                                                >
                                                    {role.name}
                                                </option>
                                            ))}
                                        </select>
                                    </td>

                                    <td>
                                        <span
                                            className={
                                                user.status === 'ACTIVE'
                                                    ? 'badge badge--active'
                                                    : 'badge'
                                            }
                                        >
                                            {user.status}
                                        </span>
                                    </td>

                                    <td>
                                        <div className="row-actions">
                                            <button
                                                type="button"
                                                className={
                                                    user.status === 'ACTIVE'
                                                        ? 'link-button danger'
                                                        : 'link-button success'
                                                }
                                                onClick={() => handleStatus(user)}
                                            >
                                                {user.status === 'ACTIVE'
                                                    ? 'Khóa'
                                                    : 'Kích hoạt'}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}

                            {users.length === 0 && (
                                <tr>
                                    <td colSpan="6">
                                        Chưa có tài khoản.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    )
}